package com.mobilemail.data.sync

import com.mobilemail.data.local.dao.PendingOperationDao
import com.mobilemail.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONArray
import org.json.JSONObject

internal fun operation(
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

internal fun sendPayload(localFilePath: String, isUploaded: Boolean = false): String = JSONObject().apply {
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
                put("isUploaded", isUploaded)
            }
        )
    )
}.toString()

internal class FakePendingOperationDao(initial: List<PendingOperationEntity>) : PendingOperationDao {
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

    override suspend fun resetInterruptedSubmissions(updatedAt: Long) {
        operations.indices.forEach { index ->
            val item = operations[index]
            if (item.status == OfflineQueueManager.STATUS_SUBMITTING) {
                operations[index] = item.copy(status = OfflineQueueManager.STATUS_UNKNOWN_OUTCOME, updatedAt = updatedAt)
            }
        }
    }

    override suspend fun getUnknownOutcomeCount(): Int = operations.count {
        it.status == OfflineQueueManager.STATUS_UNKNOWN_OUTCOME
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

    override suspend fun getPendingCountForAccount(server: String, email: String): Int =
        getAll().count { it.server == server && it.email == email && it.status in listOf("pending", "failed") }

    override suspend fun getFailedCountForAccount(server: String, email: String): Int =
        getAll().count { it.server == server && it.email == email && it.status == "failed" }

    override suspend fun getPermanentFailedCountForAccount(server: String, email: String): Int =
        getAll().count { it.server == server && it.email == email && it.status == "permanent_failed" }

    override suspend fun getPermanentFailedCount(): Int = operations.count {
        it.status == OfflineQueueManager.STATUS_PERMANENT_FAILED
    }

    override suspend fun getUnknownOutcomeCountForAccount(server: String, email: String): Int =
        getAll().count { it.server == server && it.email == email && it.status == "unknown_outcome" }

    override suspend fun getAll(): List<PendingOperationEntity> = operations.toList()

    override suspend fun insert(operation: PendingOperationEntity): Long = error("not used")

    override suspend fun deleteByAccount(server: String, email: String) = error("not used")


    override fun observeAll(): Flow<List<PendingOperationEntity>> = emptyFlow()

    override suspend fun getById(id: Long): PendingOperationEntity? = error("not used")

    override suspend fun getActiveReadStatusOperations(): List<PendingOperationEntity> = error("not used")

    override suspend fun getFailedOperations(server: String, email: String): List<PendingOperationEntity> = error("not used")

    override suspend fun delete(id: Long) = error("not used")

    override suspend fun retryById(id: Long, updatedAt: Long) = error("not used")

    override suspend fun retryAllFailed(server: String, email: String, updatedAt: Long) = error("not used")

    override suspend fun clearFailed(server: String, email: String) = error("not used")
}
