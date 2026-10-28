package com.mobilemail.data.sync

import com.mobilemail.data.local.dao.PendingOperationDao
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.domain.common.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.FileNotFoundException

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

    private fun operation(
        id: Long,
        type: String,
        payload: String = JSONObject().put("messageId", "message-$id").toString(),
        status: String = OfflineQueueManager.STATUS_PENDING
    ) = PendingOperationEntity(
        id = id,
        type = type,
        server = "https://example.com",
        email = "user@example.com",
        accountId = "account",
        payload = payload,
        status = status,
        attemptCount = 0,
        lastError = null,
        createdAt = id,
        updatedAt = id
    )

    private fun sendPayload(localFilePath: String): String = JSONObject().apply {
        put("to", JSONArray().put("recipient@example.com"))
        put("subject", "subject")
        put("body", "body")
        put("draftId", JSONObject.NULL)
        put(
            "attachments",
            JSONArray().put(
                JSONObject().apply {
                    put("id", "attachment-1")
                    put("filename", "attachment.bin")
                    put("mime", "application/octet-stream")
                    put("size", 10)
                    put("localFilePath", localFilePath)
                    put("isUploaded", false)
                }
            )
        )
    }.toString()

    private class FakePendingOperationDao(initial: List<PendingOperationEntity>) : PendingOperationDao {
        private val operations = initial.toMutableList()
        var resetRunningCalls = 0
            private set

        fun operation(id: Long): PendingOperationEntity? = operations.firstOrNull { it.id == id }

        override suspend fun resetRunning(updatedAt: Long) {
            resetRunningCalls++
            operations.indices.forEach { index ->
                val item = operations[index]
                if (item.status == OfflineQueueManager.STATUS_RUNNING) {
                    operations[index] = item.copy(status = OfflineQueueManager.STATUS_PENDING, updatedAt = updatedAt)
                }
            }
        }

        override suspend fun getProcessable(limit: Int): List<PendingOperationEntity> = operations
            .filter { it.status == OfflineQueueManager.STATUS_PENDING || it.status == OfflineQueueManager.STATUS_FAILED }
            .sortedBy { it.createdAt }
            .take(limit)

        override suspend fun updateStatus(id: Long, status: String, attemptCount: Int, lastError: String?, updatedAt: Long) {
            val index = operations.indexOfFirst { it.id == id }
            if (index >= 0) {
                operations[index] = operations[index].copy(
                    status = status,
                    attemptCount = attemptCount,
                    lastError = lastError,
                    updatedAt = updatedAt
                )
            }
        }

        override suspend fun deleteById(id: Long) {
            operations.removeAll { it.id == id }
        }

        override suspend fun getPendingCount(): Int = operations.count {
            it.status == OfflineQueueManager.STATUS_PENDING || it.status == OfflineQueueManager.STATUS_FAILED
        }

        override suspend fun getPermanentFailedCount(): Int = operations.count {
            it.status == OfflineQueueManager.STATUS_PERMANENT_FAILED
        }

        override suspend fun insert(operation: PendingOperationEntity): Long = error("not used")

        override suspend fun deleteByAccount(accountId: String) = error("not used")

        override suspend fun getFailedCount(): Int = error("not used")

        override fun observeAll(): Flow<List<PendingOperationEntity>> = emptyFlow()

        override suspend fun getById(id: Long): PendingOperationEntity? = error("not used")

        override suspend fun getFailedOperations(): List<PendingOperationEntity> = error("not used")

        override suspend fun delete(id: Long) = error("not used")

        override suspend fun retryById(id: Long, updatedAt: Long) = error("not used")

        override suspend fun retryAllFailed(updatedAt: Long) = error("not used")

        override suspend fun clearFailed() = error("not used")
    }
}
