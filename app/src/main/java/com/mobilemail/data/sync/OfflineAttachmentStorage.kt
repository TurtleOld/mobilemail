package com.mobilemail.data.sync

import android.content.Context
import java.io.File
import java.util.UUID

object OfflineAttachmentStorage {
    private const val DIRECTORY = "queued_attachments"

    fun persist(context: Context, filename: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val safeName = filename.ifBlank { "attachment.bin" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "${UUID.randomUUID()}_$safeName")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun read(path: String): ByteArray = File(path).readBytes()

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
