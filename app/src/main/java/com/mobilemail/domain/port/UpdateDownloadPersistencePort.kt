package com.mobilemail.domain.port

import com.mobilemail.domain.model.UpdateReleaseManifest

/**
 * Согласие пользователя на релиз, назначение файла, намерение загрузки и
 * download ID — то, что должно пережить восстановление процесса.
 */
data class PendingDownload(
    val manifest: UpdateReleaseManifest,
    val apkFilePath: String,
    val downloadId: Long
)

/**
 * Хранилище состояния скачивания, переживающее перезапуск процесса.
 * Не содержит Activity или callbacks и не стирается при выходе из почтового
 * аккаунта — состояние обновлений не привязано к аккаунту.
 */
interface UpdateDownloadPersistencePort {
    suspend fun savePendingDownload(pending: PendingDownload)
    suspend fun loadPendingDownload(): PendingDownload?

    /** Время фактического завершения загрузки. Повторный вызов не сдвигает уже записанное время. */
    suspend fun markCompletedNow(completedAtMillis: Long)
    suspend fun getCompletedAtMillis(): Long?

    suspend fun clear()
}
