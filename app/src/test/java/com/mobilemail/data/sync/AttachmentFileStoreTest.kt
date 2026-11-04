package com.mobilemail.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AttachmentFileStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var store: AttachmentFileStore

    @Before
    fun setUp() {
        root = temporaryFolder.newFolder("queued_attachments")
        store = AttachmentFileStore(root)
    }

    @Test
    fun `persist keeps copies of different accounts in separate directories`() {
        val first = File(store.persist("account-a", "report.pdf", byteArrayOf(1)))
        val second = File(store.persist("account-b", "report.pdf", byteArrayOf(2)))

        assertNotEquals(first.parentFile, second.parentFile)
        assertEquals("report.pdf", first.name.substringAfter('_'))
        assertTrue(first.readBytes().contentEquals(byteArrayOf(1)))
    }

    @Test
    fun `clearAccount removes only the copies of that account`() {
        val firstAccountCopy = store.persist("account-a", "a.pdf", byteArrayOf(1))
        val secondAccountCopy = store.persist("account-b", "b.pdf", byteArrayOf(2))

        store.clearAccount("account-a")

        assertFalse(File(firstAccountCopy).exists())
        assertTrue(File(secondAccountCopy).exists())
    }

    @Test
    fun `clearAll removes every copy`() {
        val firstAccountCopy = store.persist("account-a", "a.pdf", byteArrayOf(1))
        val secondAccountCopy = store.persist("account-b", "b.pdf", byteArrayOf(2))

        store.clearAll()

        assertFalse(File(firstAccountCopy).exists())
        assertFalse(File(secondAccountCopy).exists())
    }

    @Test
    fun `deleteLegacyOrphans removes only unreferenced files in the storage root`() {
        val orphan = File(root, "legacy-orphan.pdf").apply { writeBytes(byteArrayOf(1)) }
        val queued = File(root, "legacy-queued.pdf").apply { writeBytes(byteArrayOf(2)) }
        val accountCopy = store.persist("account-a", "a.pdf", byteArrayOf(3))

        store.deleteLegacyOrphans(setOf(queued.absolutePath))

        assertFalse(orphan.exists())
        assertTrue(queued.exists())
        assertTrue(File(accountCopy).exists())
    }
}
