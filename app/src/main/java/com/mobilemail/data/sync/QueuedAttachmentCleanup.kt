package com.mobilemail.data.sync

import android.app.Application
import android.util.Log
import com.mobilemail.data.local.dao.PendingOperationDao
import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Освобождает локальные копии вложений, которые больше не нужны очереди отправки.
 *
 * Копия удаляется только тогда, когда её путь не указан ни в одной операции
 * очереди. Поэтому выполняющаяся, ожидающая повтора или ещё не разобранная
 * операция всегда сохраняет исходный файл для восстановления.
 */
internal object QueuedAttachmentCleanup {
    private const val TAG = "QueuedAttachmentCleanup"

    /**
     * Удаляет копии из [paths], если их не использует ни одна операция очереди.
     *
     * Ошибки очистки не должны ломать отправку, поэтому они только логируются.
     *
     * @param application контекст приложения.
     * @param paths пути копий, которые можно освободить.
     */
    suspend fun release(application: Application, paths: Collection<String?>) {
        release(application, pendingOperationDao(application), paths)
    }

    /**
     * Удаляет копии из [paths], если их не использует ни одна операция из [dao].
     *
     * @param application контекст приложения.
     * @param dao источник операций очереди.
     * @param paths пути копий, которые можно освободить.
     */
    suspend fun release(application: Application, dao: PendingOperationDao, paths: Collection<String?>) {
        runCatching {
            withContext(Dispatchers.IO) {
                deleteUnreferenced(dao, OfflineAttachmentStorage.forContext(application), paths)
            }
        }.onFailure { Log.w(TAG, "Не удалось освободить локальные копии вложений", it) }
    }

    /**
     * Удаляет копии вложений операций [operations], не нужные остальным операциям.
     *
     * @param application контекст приложения.
     * @param operations операции, чьи копии освобождаются.
     */
    suspend fun releaseForOperations(application: Application, operations: List<PendingOperationEntity>) {
        val dao = pendingOperationDao(application)
        release(application, dao, operations.flatMap { it.attachmentPaths() })
    }

    /**
     * Удаляет копии, оставшиеся в корне хранилища от версий без каталогов аккаунтов.
     *
     * @param application контекст приложения.
     */
    suspend fun releaseLegacy(application: Application) {
        runCatching {
            withContext(Dispatchers.IO) {
                val referenced = pendingOperationDao(application).getAll()
                    .flatMap { it.attachmentPaths() }
                    .toSet()
                OfflineAttachmentStorage.forContext(application).deleteLegacyOrphans(referenced)
            }
        }.onFailure { Log.w(TAG, "Не удалось убрать старые копии вложений", it) }
    }

    /**
     * Удаляет копии из [paths], не упомянутые в операциях [dao].
     *
     * @param dao источник операций очереди.
     * @param store хранилище копий.
     * @param paths пути копий, которые можно освободить.
     */
    internal suspend fun deleteUnreferenced(
        dao: PendingOperationDao,
        store: AttachmentFileStore,
        paths: Collection<String?>
    ) {
        val candidates = paths.filterNotNull().filter { it.isNotBlank() }.toSet()
        if (candidates.isEmpty()) return
        val referenced = dao.getAll().flatMap { it.attachmentPaths() }.toSet()
        (candidates - referenced).forEach { store.delete(it) }
    }

    private fun pendingOperationDao(application: Application): PendingOperationDao =
        AppDatabase.getInstance(application).pendingOperationDao()

    private fun PendingOperationEntity.attachmentPaths(): List<String> =
        if (type == OfflineQueueManager.TYPE_SEND) sendAttachmentPaths(payload) else emptyList()
}
