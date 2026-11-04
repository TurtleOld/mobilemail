package com.mobilemail.ui.newmessage

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Максимальный размер вложения, добавляемого к письму: 10 МБ. */
const val MAX_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024L

/** Сообщение об отказе для вложения больше [MAX_ATTACHMENT_SIZE_BYTES]. */
const val ATTACHMENT_TOO_LARGE_MESSAGE = "Размер вложения больше 10 МБ"

/**
 * Читает вложение, не загружая в память больше [MAX_ATTACHMENT_SIZE_BYTES] + 1 байта.
 *
 * Заявленный поставщиком размер [declaredSize] используется только для быстрого отказа;
 * решение принимается по фактическому числу байт в потоке, поэтому неизвестный
 * (`null`) или заниженный размер не позволяет обойти лимит.
 *
 * @param declaredSize размер файла, заявленный поставщиком контента, или `null`, если он неизвестен.
 * @param openStream открывает поток вложения; вызывается не более одного раза.
 * @return содержимое вложения, если оно не превышает [MAX_ATTACHMENT_SIZE_BYTES].
 * @throws IllegalArgumentException если вложение больше [MAX_ATTACHMENT_SIZE_BYTES].
 * @throws IllegalStateException если поток не удалось открыть.
 */
fun readAttachmentBytes(
    declaredSize: Long?,
    openStream: () -> InputStream?
): ByteArray {
    require(declaredSize == null || declaredSize <= MAX_ATTACHMENT_SIZE_BYTES) { ATTACHMENT_TOO_LARGE_MESSAGE }

    val input = openStream() ?: error("Не удалось прочитать файл")
    val limit = MAX_ATTACHMENT_SIZE_BYTES + 1
    return input.use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val bytes = ByteArrayOutputStream(minOf(limit, buffer.size.toLong()).toInt())
        var total = 0L
        while (total < limit) {
            val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
            if (read <= 0) break
            bytes.write(buffer, 0, read)
            total += read
        }
        require(total <= MAX_ATTACHMENT_SIZE_BYTES) { ATTACHMENT_TOO_LARGE_MESSAGE }
        bytes.toByteArray()
    }
}
