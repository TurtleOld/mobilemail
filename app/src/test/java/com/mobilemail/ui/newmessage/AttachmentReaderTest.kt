package com.mobilemail.ui.newmessage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Arrays

class AttachmentReaderTest {

    private val limit = MAX_ATTACHMENT_SIZE_BYTES

    @Test
    fun `unknown declared size accepts stream of exactly the limit`() {
        val source = CountingInputStream(limit)

        val bytes = readAttachmentBytes(declaredSize = null) { source }

        assertEquals(limit.toInt(), bytes.size)
        assertEquals(limit, source.bytesRead)
        assertTrue(source.closed)
    }

    @Test
    fun `empty stream is accepted`() {
        val source = CountingInputStream(total = 0)

        val bytes = readAttachmentBytes(declaredSize = null) { source }

        assertArrayEquals(ByteArray(0), bytes)
        assertTrue(source.closed)
    }

    @Test
    fun `stream of limit plus one byte is rejected`() {
        val source = CountingInputStream(limit + 1)

        assertTooLarge { readAttachmentBytes(declaredSize = null) { source } }

        assertEquals(limit + 1, source.bytesRead)
        assertTrue(source.closed)
    }

    @Test
    fun `unknown declared size rejects stream above the limit`() {
        val source = CountingInputStream(total = Long.MAX_VALUE)

        assertTooLarge { readAttachmentBytes(declaredSize = null) { source } }

        assertTrue(source.closed)
    }

    @Test
    fun `wrong small declared size does not bypass the limit`() {
        val source = CountingInputStream(limit + 1)

        assertTooLarge { readAttachmentBytes(declaredSize = 1) { source } }

        assertTrue(source.closed)
    }

    @Test
    fun `declared size above the limit is rejected without opening the stream`() {
        var opened = false

        assertTooLarge {
            readAttachmentBytes(declaredSize = limit + 1) {
                opened = true
                CountingInputStream(total = 0)
            }
        }

        assertFalse(opened)
    }

    @Test
    fun `read stops after limit plus one bytes even for an endless stream`() {
        val source = CountingInputStream(total = Long.MAX_VALUE)

        assertTooLarge { readAttachmentBytes(declaredSize = null) { source } }

        assertEquals("должно быть прочитано не более лимита плюс один байт", limit + 1, source.bytesRead)
    }

    @Test
    fun `missing stream reports a read failure`() {
        val error = runCatching { readAttachmentBytes(declaredSize = null) { null } }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("Не удалось прочитать файл", error?.message)
    }

    @Test
    fun `bytes are returned unchanged`() {
        val payload = ByteArray(32) { it.toByte() }

        val bytes = readAttachmentBytes(declaredSize = payload.size.toLong()) {
            ByteArrayInputStream(payload)
        }

        assertArrayEquals(payload, bytes)
    }

    private fun assertTooLarge(block: () -> Unit) {
        try {
            block()
            fail("ожидался отказ по размеру")
        } catch (e: IllegalArgumentException) {
            assertEquals(ATTACHMENT_TOO_LARGE_MESSAGE, e.message)
        }
    }

    private class CountingInputStream(private val total: Long) : InputStream() {
        var bytesRead = 0L
            private set
        var closed = false
            private set

        override fun read(): Int {
            if (bytesRead >= total) return -1
            bytesRead++
            return 0
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (bytesRead >= total) return -1
            val count = minOf(len.toLong(), total - bytesRead).toInt()
            Arrays.fill(b, off, off + count, 0)
            bytesRead += count
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
