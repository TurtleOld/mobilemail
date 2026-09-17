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
 * [UpdateDownloadPersistencePort]; [restorePendingDownload] должен вызываться
 * один раз при создании держателя координатора.
 */
class UpdateDownloadCoordinator(
    private val downloadPort: UpdateDownloadPort,
    private val verifier: ApkVerifierPort,
    private val store: UpdateDownloadPersistencePort,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    private val downloadMutex = Mutex()
    private var active: ActiveDownload? = null
    private var monitorJob: Job? = null

    /** Назначение файла последней попытки — переживает [failDownload], чтобы «Повторить» знало, куда качать снова. */
    private var lastApkFilePath: String? = null

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
                lastApkFilePath = pending.apkFilePath
                monitorJob = scope.launch { monitorDownload(pending.downloadId, pending.manifest) }
            }
        }
    }

    /**
     * Пользователь согласился скачать [manifest]. Повторный вызов с тем же
     * versionCode, пока загрузка уже идёт, не создаёт вторую загрузку.
     */
    fun startDownload(scope: CoroutineScope, manifest: UpdateReleaseManifest, apkFilePath: String) {
        scope.launch {
            downloadMutex.withLock {
                if (isAlreadyDownloading(manifest)) return@withLock
                _state.value = UpdateDownloadState.Requesting
                val downloadId = downloadPort.enqueue(manifest)
                active = ActiveDownload(downloadId, manifest, apkFilePath)
                lastApkFilePath = apkFilePath
                store.savePendingDownload(PendingDownload(manifest, apkFilePath, downloadId))
                monitorJob = scope.launch { monitorDownload(downloadId, manifest) }
            }
        }
    }

    private fun isAlreadyDownloading(manifest: UpdateReleaseManifest): Boolean {
        val current = active ?: return false
        val isInProgressState = _state.value.let {
            it is UpdateDownloadState.Requesting ||
                it is UpdateDownloadState.Downloading ||
                it is UpdateDownloadState.WaitingForNetwork ||
                it is UpdateDownloadState.Verifying
        }
        return current.manifest.versionCode == manifest.versionCode && isInProgressState
    }

    /** Отмена по запросу пользователя: останавливает системную загрузку и удаляет файл. */
    fun cancelDownload(scope: CoroutineScope) {
        scope.launch {
            downloadMutex.withLock {
                val current = active ?: return@withLock
                monitorJob?.cancel()
                monitorJob = null
                downloadPort.cancel(current.downloadId)
                active = null
                store.clear()
                _state.value = UpdateDownloadState.Cancelled
            }
        }
    }

    /** Повтор после ошибки: новая загрузка того же релиза и того же назначения файла. */
    fun retryDownload(scope: CoroutineScope) {
        val failed = _state.value as? UpdateDownloadState.Failed ?: return
        val apkFilePath = lastApkFilePath ?: return
        startDownload(scope, failed.manifest, apkFilePath)
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
