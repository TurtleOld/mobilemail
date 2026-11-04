package com.mobilemail.data.sync

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Постоянное хранилище локальных копий вложений исходящих писем.
 *
 * Копии каждого аккаунта лежат в отдельном каталоге, поэтому очистка данных
 * одного аккаунта не затрагивает копии другого.
 *
 * @param root корневой каталог хранилища.
 */
class AttachmentFileStore(private val root: File) {

    /**
     * Сохраняет копию вложения в каталоге аккаунта.
     *
     * @param accountId аккаунт, которому принадлежит вложение.
     * @param filename исходное имя файла, используется для расширения.
     * @param bytes содержимое вложения.
     * @return абсолютный путь сохранённой копии.
     */
    fun persist(accountId: String, filename: String, bytes: ByteArray): String {
        val dir = accountDir(accountId).apply { mkdirs() }
        val safeName = filename.ifBlank { DEFAULT_FILENAME }.replace(INVALID_NAME_CHARS, "_")
        val file = File(dir, "${UUID.randomUUID()}_$safeName")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /**
     * Удаляет копию по пути. Отсутствующий или пустой путь игнорируется.
     *
     * @param path абсолютный путь копии.
     */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    /**
     * Удаляет все копии аккаунта вместе с его каталогом.
     *
     * @param accountId аккаунт, копии которого нужно удалить.
     */
    fun clearAccount(accountId: String) {
        accountDir(accountId).deleteRecursively()
    }

    /** Удаляет копии всех аккаунтов. */
    fun clearAll() {
        root.deleteRecursively()
    }

    /**
     * Удаляет копии, оставшиеся в корне хранилища от версий без каталогов
     * аккаунтов. Файлы из [referencedPaths] сохраняются, потому что они ещё
     * нужны очереди отправки.
     *
     * @param referencedPaths пути копий, используемых операциями очереди.
     */
    fun deleteLegacyOrphans(referencedPaths: Set<String>) {
        val legacyFiles = root.listFiles { file -> file.isFile } ?: return
        legacyFiles.filterNot { it.absolutePath in referencedPaths }.forEach { it.delete() }
    }

    private fun accountDir(accountId: String): File {
        val safeAccountId = accountId.ifBlank { UNKNOWN_ACCOUNT }.replace(INVALID_NAME_CHARS, "_")
        val suffix = accountId.hashCode().toUInt().toString(16)
        return File(root, "${safeAccountId}_$suffix")
    }

    private companion object {
        val INVALID_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
        const val DEFAULT_FILENAME = "attachment.bin"
        const val UNKNOWN_ACCOUNT = "unknown"
    }
}

/** Доступ к постоянному хранилищу копий вложений. */
object OfflineAttachmentStorage {
    private const val DIRECTORY = "queued_attachments"

    /**
     * Возвращает хранилище копий вложений приложения.
     *
     * @param context контекст приложения или активити.
     */
    fun forContext(context: Context): AttachmentFileStore =
        AttachmentFileStore(File(context.filesDir, DIRECTORY))

    /**
     * Читает копию вложения.
     *
     * @param path абсолютный путь копии.
     * @return содержимое вложения.
     */
    fun read(path: String): ByteArray = File(path).readBytes()
}
