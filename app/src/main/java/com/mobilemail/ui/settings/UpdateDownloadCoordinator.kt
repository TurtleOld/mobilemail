package com.mobilemail.ui.settings

import com.mobilemail.domain.model.UpdateDownloadProgress
import com.mobilemail.domain.model.UpdateDownloadState
import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.ApkVerificationResult
import com.mobilemail.domain.port.ApkVerifierPort
import com.mobilemail.domain.port.DownloadStatus
import com.mobilemail.domain.port.PendingDownload
import com.mobilemail.domain.port.UpdateDownloadPersistencePort
import com.mobilemail.domain.port.UpdateDownloadPort
import com.mobilemail.ui.common.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val POLL_INTERVAL_MILLIS = 500L

private data class ActiveDownload(
    val downloadId: Long,
    val manifest: UpdateReleaseManifest,
    val apkFilePath: String
)

/**
 * Координирует скачивание APK через системный DownloadManager: согласие,
 * прогресс, ожидание сети, отмену и проверку скачанного файла.
 *
 * Живёт один экземпляр на процесс (см. [UpdateDownloadCoordinatorHolder]),
 * не хранит Activity или callbacks — только [UpdateDownloadPort]/[ApkVerifierPort]
 * и [UpdateDownloadPersistencePort]. Согласие пользователя, назначение файла,
 * намерение загрузки и download ID переживают восстановление процесса через
 * [UpdateDownloadPersistencePort].
 *
 * Восстановление ([reconcile]) сверяет сохранённую операцию с фактическим
 * состоянием DownloadManager, а не с одним сигналом о завершении: активная
 * загрузка, ожидание сети, ошибка и готовое обновление восстанавливаются из
 * реального статуса, исчезнувший файл не остаётся готовым к установке.
 */
class UpdateDownloadCoordinator(
    private val downloadPort: UpdateDownloadPort,
    private val verifier: ApkVerifierPort,
    private val store: UpdateDownloadPersistencePort,
    private val apkFilePathFor: (UpdateReleaseManifest) -> String,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    private val downloadMutex = Mutex()
    private var active: ActiveDownload? = null
    private var monitorJob: Job? = null
    private var autoContinueEligible = false

    /**
     * Уход пользователя в фон во время загрузки снимает автоматический переход
     * к установке для текущей попытки: после возвращения готовое обновление
     * предлагается кнопками «Установить» / «Позже».
     */
    fun onAppBackgrounded() {
        autoContinueEligible = false
    }

    /**
     * Сверяет сохранённую операцию с фактическим состоянием DownloadManager.
     * Идемпотентна и вызывается при запуске процесса и возвращении в приложение.
     * Если передан [signaledDownloadId], операция сверяется только тогда, когда
     * он относится к сохранённой загрузке. Восстановленное готовое обновление
     * никогда не продолжается к установке автоматически.
     */
    fun reconcile(scope: CoroutineScope, signaledDownloadId: Long? = null) {
        scope.launch {
            downloadMutex.withLock {
                if (monitorJob?.isActive == true) return@withLock
                val pending = store.loadPendingDownload() ?: return@withLock
                if (signaledDownloadId != null && pending.downloadId != null && pending.downloadId != signaledDownloadId) {
                    return@withLock
                }
                if (isAlreadyRestored(pending)) return@withLock
                restoreLocked(scope, pending)
            }
        }
    }

    /**
     * Пользователь согласился скачать [manifest]. Повторный вызов с тем же
     * versionCode, пока загрузка уже идёт, не создаёт вторую загрузку.
     */
    fun startDownload(scope: CoroutineScope, manifest: UpdateReleaseManifest) {
        scope.launch {
            downloadMutex.withLock {
                when (downloadInProgressFor(active)) {
                    manifest.versionCode -> return@withLock
                    null -> Unit
                    else -> stopActiveDownloadLocked()
                }
                val apkFilePath = apkFilePathFor(manifest)
                autoContinueEligible = true
                if (store.getCompletedAtMillis() == null) {
                    store.loadPendingDownload()?.downloadId?.let { downloadPort.cancel(it) }
                }
                store.savePendingDownload(PendingDownload(manifest, apkFilePath, downloadId = null))
                enqueueAndTrackLocked(scope, manifest, apkFilePath)
            }
        }
    }

    /**
     * Ставит согласованную загрузку в системную очередь, сохраняет её ID и
     * начинает наблюдение за фактическим статусом.
     */
    private suspend fun enqueueAndTrackLocked(
        scope: CoroutineScope,
        manifest: UpdateReleaseManifest,
        apkFilePath: String
    ) {
        _state.value = UpdateDownloadState.Requesting
        val downloadId = downloadPort.enqueue(manifest)
        store.saveDownloadId(downloadId)
        active = ActiveDownload(downloadId, manifest, apkFilePath)
        monitorJob = scope.launch { monitorDownload(downloadId, manifest) }
    }

    /** versionCode активной загрузки, если она ещё идёт; `null`, если активной загрузки нет. */
    private fun downloadInProgressFor(current: ActiveDownload?): Int? {
        val isInProgressState = _state.value.let {
            it is UpdateDownloadState.Requesting ||
                it is UpdateDownloadState.Downloading ||
                it is UpdateDownloadState.WaitingForNetwork ||
                it is UpdateDownloadState.Verifying
        }
        return current?.manifest?.versionCode?.takeIf { isInProgressState }
    }

    /**
     * Согласие на другой релиз, пока предыдущая загрузка ещё идёт: она не остаётся
     * осиротевшей — сначала очищается сохранённое состояние, затем останавливается
     * системная загрузка, после чего [startDownload] продолжает с новым релизом.
     */
    private suspend fun stopActiveDownloadLocked() {
        val current = active
        autoContinueEligible = false
        store.clear()
        active = null
        monitorJob?.cancel()
        monitorJob = null
        if (current != null) downloadPort.cancel(current.downloadId)
    }

    /** Отмена по запросу пользователя: останавливает системную загрузку и удаляет файл. */
    fun cancelDownload(scope: CoroutineScope) {
        scope.launch {
            downloadMutex.withLock {
                val pending = store.loadPendingDownload()
                if (active == null && pending == null) return@withLock
                val hadActive = active != null
                stopActiveDownloadLocked()
                val leftoverId = pending?.downloadId
                if (!hadActive && leftoverId != null) downloadPort.cancel(leftoverId)
                _state.value = UpdateDownloadState.Cancelled
            }
        }
    }

    /** Повтор после ошибки: новая загрузка того же релиза и того же назначения файла. */
    fun retryDownload(scope: CoroutineScope) {
        val failed = _state.value as? UpdateDownloadState.Failed ?: return
        startDownload(scope, failed.manifest)
    }

    companion object {
        /**
         * Создаёт координатор и сразу сверяет сохранённую операцию с фактическим
         * состоянием DownloadManager. Единственный способ создать координатор,
         * готовый к использованию держателем (см. [UpdateDownloadCoordinatorHolder]);
         * обычный конструктор годится только для тестов, которым нужен точный
         * контроль над моментом восстановления.
         */
        fun createAndRestore(
            scope: CoroutineScope,
            downloadPort: UpdateDownloadPort,
            verifier: ApkVerifierPort,
            store: UpdateDownloadPersistencePort,
            apkFilePathFor: (UpdateReleaseManifest) -> String,
            now: () -> Long = System::currentTimeMillis
        ): UpdateDownloadCoordinator {
            val coordinator = UpdateDownloadCoordinator(downloadPort, verifier, store, apkFilePathFor, now)
            coordinator.reconcile(scope)
            return coordinator
        }
    }

    private fun isAlreadyRestored(pending: PendingDownload): Boolean {
        val current = _state.value
        return current is UpdateDownloadState.Ready && current.apkFilePath == pending.apkFilePath
    }

    /**
     * Восстанавливает сохранённую операцию по реальному состоянию системы.
     * Завершённая загрузка перепроверяется по файлу, незавершённая — либо
     * подхватывается по download ID, либо находится по сохранённому назначению,
     * а если постановки ещё не было — ставится один раз без нового согласия.
     */
    private suspend fun restoreLocked(scope: CoroutineScope, pending: PendingDownload) {
        if (store.getCompletedAtMillis() != null) {
            restoreCompletedLocked(pending)
            return
        }
        val downloadId = pending.downloadId ?: downloadPort.findDownloadIdByDestination(pending.apkFilePath)
        if (downloadId == null) {
            resumeUnqueuedDownloadLocked(scope, pending)
            return
        }
        store.saveDownloadId(downloadId)
        active = ActiveDownload(downloadId, pending.manifest, pending.apkFilePath)
        autoContinueEligible = false
        monitorJob = scope.launch { monitorDownload(downloadId, pending.manifest) }
    }

    private suspend fun resumeUnqueuedDownloadLocked(scope: CoroutineScope, pending: PendingDownload) {
        autoContinueEligible = false
        enqueueAndTrackLocked(scope, pending.manifest, pending.apkFilePath)
    }

    private suspend fun restoreCompletedLocked(pending: PendingDownload) {
        when (val result = verifier.verify(pending.apkFilePath, pending.manifest)) {
            ApkVerificationResult.Valid -> {
                active = null
                autoContinueEligible = false
                _state.value = UpdateDownloadState.Ready(pending.manifest, pending.apkFilePath, autoContinue = false)
            }
            is ApkVerificationResult.Invalid -> {
                active = null
                store.clear()
                _state.value = UpdateDownloadState.Failed(
                    error = AppError.UnknownError(errorMessage = result.reason),
                    manifest = pending.manifest
                )
            }
        }
    }

    private suspend fun monitorDownload(downloadId: Long, manifest: UpdateReleaseManifest) {
        while (true) {
            when (val status = downloadPort.pollStatus(downloadId)) {
                DownloadStatus.Pending -> _state.value = UpdateDownloadState.Requesting
                is DownloadStatus.Running -> {
                    _state.value = UpdateDownloadState.Downloading(
                        UpdateDownloadProgress(status.bytesDownloaded, status.totalBytes)
                    )
                }
                DownloadStatus.Paused -> _state.value = UpdateDownloadState.WaitingForNetwork
                is DownloadStatus.Successful -> {
                    onDownloadFinished(downloadId) { verifyAndComplete(manifest, status.filePath) }
                    return
                }
                is DownloadStatus.Failed -> {
                    onDownloadFinished(downloadId) { failDownload(manifest, status.reason, clearRecord = false) }
                    return
                }
                DownloadStatus.NotFound -> {
                    onDownloadFinished(downloadId) {
                        failDownload(manifest, "Загрузка была удалена вне приложения", clearRecord = true)
                    }
                    return
                }
            }
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    /**
     * Поздний сигнал завершения об уже отменённой/сброшенной загрузке (downloadId
     * больше не совпадает с активной) игнорируется — координатор не возвращается
     * в активное состояние из-за события, относящегося к прошлой загрузке.
     */
    private suspend fun onDownloadFinished(downloadId: Long, onCurrent: suspend () -> Unit) {
        downloadMutex.withLock {
            if (active?.downloadId != downloadId) return@withLock
            onCurrent()
        }
    }

    private suspend fun verifyAndComplete(manifest: UpdateReleaseManifest, filePath: String) {
        _state.value = UpdateDownloadState.Verifying
        when (val result = verifier.verify(filePath, manifest)) {
            ApkVerificationResult.Valid -> {
                store.markCompletedNow(now())
                active = null
                val autoContinue = autoContinueEligible
                autoContinueEligible = false
                _state.value = UpdateDownloadState.Ready(manifest, filePath, autoContinue = autoContinue)
            }
            is ApkVerificationResult.Invalid -> {
                active = null
                autoContinueEligible = false
                store.clear()
                _state.value = UpdateDownloadState.Failed(
                    error = AppError.UnknownError(errorMessage = result.reason),
                    manifest = manifest
                )
            }
        }
    }

    private suspend fun failDownload(manifest: UpdateReleaseManifest, reason: String, clearRecord: Boolean) {
        active = null
        autoContinueEligible = false
        if (clearRecord) {
            store.clear()
        }
        _state.value = UpdateDownloadState.Failed(
            error = AppError.UnknownError(errorMessage = reason),
            manifest = manifest
        )
    }
}
