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
 * [UpdateDownloadPersistencePort]; создавайте координатор через [createAndRestore],
 * чтобы это восстановление гарантированно произошло.
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

    /**
     * Восстанавливает наблюдение за загрузкой, которая была начата до перезапуска
     * процесса. Вызывается один раз на процесс, до первого обращения пользователя
     * к экрану загрузки.
     */
    fun restorePendingDownload(scope: CoroutineScope) {
        scope.launch {
            val pending = store.loadPendingDownload() ?: return@launch
            downloadMutex.withLock {
                active = ActiveDownload(pending.downloadId, pending.manifest, pending.apkFilePath)
                monitorJob = scope.launch { monitorDownload(pending.downloadId, pending.manifest) }
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
                _state.value = UpdateDownloadState.Requesting
                val downloadId = downloadPort.enqueue(manifest)
                active = ActiveDownload(downloadId, manifest, apkFilePath)
                store.savePendingDownload(PendingDownload(manifest, apkFilePath, downloadId))
                monitorJob = scope.launch { monitorDownload(downloadId, manifest) }
            }
        }
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
     * осиротевшей — сначала останавливается системная загрузка и очищается состояние,
     * затем [startDownload] продолжает уже с новым релизом.
     */
    private suspend fun stopActiveDownloadLocked() {
        val current = active ?: return
        monitorJob?.cancel()
        monitorJob = null
        downloadPort.cancel(current.downloadId)
        active = null
        store.clear()
    }

    /** Отмена по запросу пользователя: останавливает системную загрузку и удаляет файл. */
    fun cancelDownload(scope: CoroutineScope) {
        scope.launch {
            downloadMutex.withLock {
                if (active == null) return@withLock
                stopActiveDownloadLocked()
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
         * Создаёт координатор и сразу восстанавливает загрузку, начатую до перезапуска
         * процесса. Единственный способ создать координатор, готовый к использованию
         * держателем (см. [UpdateDownloadCoordinatorHolder]) — обычный конструктор
         * оставляет восстановление на совести вызывающего и годится только для тестов,
         * которым нужен точный контроль над моментом восстановления.
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
            coordinator.restorePendingDownload(scope)
            return coordinator
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
                    onDownloadFinished(downloadId) { failDownload(manifest, status.reason) }
                    return
                }
                DownloadStatus.NotFound -> {
                    onDownloadFinished(downloadId) { failDownload(manifest, "Загрузка была удалена вне приложения") }
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
                _state.value = UpdateDownloadState.Ready(manifest, filePath)
            }
            is ApkVerificationResult.Invalid -> {
                active = null
                store.clear()
                _state.value = UpdateDownloadState.Failed(
                    error = AppError.UnknownError(errorMessage = result.reason),
                    manifest = manifest
                )
            }
        }
    }

    private suspend fun failDownload(manifest: UpdateReleaseManifest, reason: String) {
        active = null
        store.clear()
        _state.value = UpdateDownloadState.Failed(
            error = AppError.UnknownError(errorMessage = reason),
            manifest = manifest
        )
    }
}
