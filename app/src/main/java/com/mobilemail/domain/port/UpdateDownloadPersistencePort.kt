package com.mobilemail.domain.port

import com.mobilemail.domain.model.UpdateReleaseManifest

/**
 * Согласие пользователя на релиз, назначение файла, намерение загрузки и
 * download ID — то, что должно пережить восстановление процесса.
 *
 * [downloadId] равен `null`, пока системная загрузка не поставлена в очередь:
 * согласие и назначение сохраняются до `enqueue`, поэтому завершение процесса
 * между постановкой и записью ID не теряет операцию — её находят по
 * сохранённому [apkFilePath].
 */
data class PendingDownload(
    val manifest: UpdateReleaseManifest,
    val apkFilePath: String,
    val downloadId: Long?
)

/**
 * Хранилище состояния скачивания, переживающее перезапуск процесса.
 * Не содержит Activity или callbacks и не стирается при выходе из почтового
 * аккаунта — состояние обновлений не привязано к аккаунту.
 */
interface UpdateDownloadPersistencePort {
    suspend fun savePendingDownload(pending: PendingDownload)
    suspend fun loadPendingDownload(): PendingDownload?

    /** Записывает download ID поверх сохранённого намерения после `enqueue`. */
    suspend fun saveDownloadId(downloadId: Long)

    /** Время фактического завершения загрузки. Повторный вызов не сдвигает уже записанное время. */
    suspend fun markCompletedNow(completedAtMillis: Long)
    suspend fun getCompletedAtMillis(): Long?

    suspend fun clear()
}
