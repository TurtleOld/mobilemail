package com.mobilemail.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QueuedAttachmentCleanupTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var store: AttachmentFileStore

    @Before
    fun setUp() {
        store = AttachmentFileStore(temporaryFolder.newFolder("queued_attachments"))
    }

    @Test
    fun `confirmed send releases the copy that no queue operation needs`() = runTest {
        val path = store.persist("account", "photo.jpg", byteArrayOf(1))
        val dao = FakePendingOperationDao(emptyList())

        QueuedAttachmentCleanup.deleteUnreferenced(dao, store, listOf(path))

        assertFalse(File(path).exists())
    }

    @Test
    fun `copy is kept while a pending send still references it`() = runTest {
        val path = store.persist("account", "photo.jpg", byteArrayOf(1))
        val dao = FakePendingOperationDao(listOf(operation(id = 1, type = OfflineQueueManager.TYPE_SEND, payload = sendPayload(path))))

        QueuedAttachmentCleanup.deleteUnreferenced(dao, store, listOf(path))

        assertTrue(File(path).exists())
    }

    @Test
    fun `copy is kept while a running send of an already uploaded attachment references it`() = runTest {
        val path = store.persist("account", "photo.jpg", byteArrayOf(1))
        val dao = FakePendingOperationDao(
            listOf(
                operation(
                    id = 1,
                    type = OfflineQueueManager.TYPE_SEND,
                    payload = sendPayload(path, isUploaded = true),
                    status = OfflineQueueManager.STATUS_RUNNING
                )
            )
        )

        QueuedAttachmentCleanup.deleteUnreferenced(dao, store, listOf(path))

        assertTrue(File(path).exists())
    }

    @Test
    fun `copy is kept when another operation of the same account needs it`() = runTest {
        val path = store.persist("account", "photo.jpg", byteArrayOf(1))
        val dao = FakePendingOperationDao(
            listOf(operation(id = 1, type = OfflineQueueManager.TYPE_SEND, payload = sendPayload(path)))
        )

        QueuedAttachmentCleanup.deleteUnreferenced(dao, store, listOf(path, "  ", null))

        assertTrue(File(path).exists())
    }

    @Test
    fun `blank paths are ignored without touching the queue`() = runTest {
        val dao = FakePendingOperationDao(emptyList())

        QueuedAttachmentCleanup.deleteUnreferenced(dao, store, listOfNotNull(null, "", "   "))
    }
}
