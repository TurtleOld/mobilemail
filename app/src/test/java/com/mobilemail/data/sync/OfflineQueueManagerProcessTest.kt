package com.mobilemail.data.sync

import com.mobilemail.domain.common.Result
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class OfflineQueueManagerProcessTest {

    @Test
    fun `send with missing attachment is marked failed and the next operation is still processed`() = runTest {
        val missingPath = "/tmp/mobilemail-missing-${System.nanoTime()}/attachment.bin"
        val send = operation(id = 1, type = OfflineQueueManager.TYPE_SEND, payload = sendPayload(missingPath))
        val delete = operation(id = 2, type = OfflineQueueManager.TYPE_DELETE)
        val dao = FakePendingOperationDao(listOf(send, delete))
        val processed = mutableListOf<Long>()

        val summary = OfflineQueueManager.processOperations(dao, limit = 25) { pending ->
            if (pending.type == OfflineQueueManager.TYPE_SEND) {
                val path = JSONObject(pending.payload)
                    .getJSONArray("attachments")
                    .getJSONObject(0)
                    .getString("localFilePath")
                OfflineAttachmentStorage.read(path)
                Result.Success(Unit)
            } else {
                processed += pending.id
                Result.Success(Unit)
            }
        }

        assertThrows(FileNotFoundException::class.java) { OfflineAttachmentStorage.read(missingPath) }
        assertEquals(OfflineQueueManager.STATUS_FAILED, dao.operation(1)?.status)
        assertNull(dao.operation(2))
        assertEquals(listOf(2L), processed)
        assertEquals(1, summary.processedCount)
        assertEquals(1, summary.failedCount)
    }

    @Test
    fun `operation left running by a killed process is processed again`() = runTest {
        val stuck = operation(id = 7, type = OfflineQueueManager.TYPE_DELETE, status = OfflineQueueManager.STATUS_RUNNING)
        val dao = FakePendingOperationDao(listOf(stuck))
        val processed = mutableListOf<Long>()

        val summary = OfflineQueueManager.processOperations(dao, limit = 25) { pending ->
            processed += pending.id
            Result.Success(Unit)
        }

        assertEquals(1, dao.resetRunningCalls)
        assertEquals(listOf(7L), processed)
        assertNull(dao.operation(7))
        assertEquals(1, summary.processedCount)
    }

    @Test
    fun `successful operation releases its attachments after its row is gone`() = runTest {
        val send = operation(id = 1, type = OfflineQueueManager.TYPE_SEND, payload = sendPayload("/tmp/attachment.bin"))
        val dao = FakePendingOperationDao(listOf(send))
        val released = mutableListOf<Long>()

        OfflineQueueManager.processOperations(
            dao = dao,
            limit = 25,
            releaseAttachments = { pending ->
                assertNull(dao.operation(pending.id))
                released += pending.id
            }
        ) { Result.Success(Unit) }

        assertEquals(listOf(1L), released)
    }

    @Test
    fun `failed operation keeps its attachments`() = runTest {
        val send = operation(id = 1, type = OfflineQueueManager.TYPE_SEND, payload = sendPayload("/tmp/attachment.bin"))
        val dao = FakePendingOperationDao(listOf(send))
        val released = mutableListOf<Long>()

        OfflineQueueManager.processOperations(
            dao = dao,
            limit = 25,
            releaseAttachments = { pending -> released += pending.id }
        ) { Result.Error(IOException("network is down")) }

        assertEquals(emptyList<Long>(), released)
        assertEquals(OfflineQueueManager.STATUS_FAILED, dao.operation(1)?.status)
    }
}
