package com.mobilemail.domain.model

import com.mobilemail.ui.common.AppError

/**
 * Прогресс скачивания APK внутри приложения.
 *
 * [totalBytes] равен `null`, когда системный DownloadManager ещё не узнал
 * общий размер: в этом случае процент не считается и не выдумывается.
 */
data class UpdateDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?
)

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data object Requesting : UpdateDownloadState()
    data class Downloading(val progress: UpdateDownloadProgress) : UpdateDownloadState()
    data object WaitingForNetwork : UpdateDownloadState()
    data object Verifying : UpdateDownloadState()

    /**
     * Готовое обновление. [autoContinue] равно `true`, только когда пользователь
     * дождался завершения загрузки в текущей попытке, не уходя в фон: в этом
     * случае разрешён один автоматический переход к установке. Восстановленное
     * после перезапуска процесса готовое обновление всегда [autoContinue] = `false`.
     */
    data class Ready(
        val manifest: UpdateReleaseManifest,
        val apkFilePath: String,
        val autoContinue: Boolean = false
    ) : UpdateDownloadState()

    data object Cancelled : UpdateDownloadState()

    /**
     * Срок хранения скачанного APK истёк: файл удалён, обновление больше не
     * Готово и не передаётся установщику. Повторное скачивание возможно только
     * после нового согласия пользователя.
     */
    data class Expired(val manifest: UpdateReleaseManifest) : UpdateDownloadState()
    data class Failed(val error: AppError, val manifest: UpdateReleaseManifest) : UpdateDownloadState()
}
