package com.mobilemail.domain.port

import com.mobilemail.domain.model.UpdateReleaseManifest

/**
 * Наблюдаемый статус одной системной загрузки, как его видит DownloadManager.
 */
sealed class DownloadStatus {
    data object Pending : DownloadStatus()
    data class Running(val bytesDownloaded: Long, val totalBytes: Long?) : DownloadStatus()
    data object Paused : DownloadStatus()
    data class Successful(val filePath: String) : DownloadStatus()
    data class Failed(val reason: String) : DownloadStatus()
    /** Download ID неизвестен системе — например, после ручного удаления пользователем из системного менеджера. */
    data object NotFound : DownloadStatus()
}

/**
 * Граница к системному загрузчику (Android DownloadManager в проде).
 *
 * Каждый метод синхронный и быстрый — сам мониторинг прогресса ведётся
 * через [pollStatus], вызываемый координатором на таймере.
 */
interface UpdateDownloadPort {
    /** Ставит APK в системную очередь загрузки, возвращает download ID. */
    fun enqueue(manifest: UpdateReleaseManifest): Long

    fun pollStatus(downloadId: Long): DownloadStatus

    /** Останавливает системную загрузку и убирает частично скачанный файл. */
    fun cancel(downloadId: Long)
}
