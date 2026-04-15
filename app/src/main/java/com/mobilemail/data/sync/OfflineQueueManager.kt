package com.mobilemail.data.sync

import android.app.Application
import com.mobilemail.data.common.Result
import com.mobilemail.data.common.fold
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.data.model.Attachment
import com.mobilemail.data.repository.ComposeRepository
import com.mobilemail.data.repository.MessageActionsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class OfflineQueueSummary(
    val processedCount: Int,
    val failedCount: Int,
    val pendingCount: Int,
    val permanentFailedCount: Int = 0
)

data class OfflineQueueStats(
    val pendingCount: Int,
    val failedCount: Int,
    val permanentFailedCount: Int,
    val totalCount: Int
)

object OfflineQueueManager {
    const val TYPE_SEND = "send"
    const val TYPE_DELETE = "delete"
    const val TYPE_MOVE = "move"
    const val TYPE_MARK_READ = "mark_read"
    const val TYPE_TOGGLE_STAR = "toggle_star"

    const val STATUS_PENDING = "pending"
    const val STATUS_FAILED = "failed"
    const val STATUS_RUNNING = "running"
    const val STATUS_PERMANENT_FAILED = "permanent_failed"

    private val mutex = Mutex()

    suspend fun enqueueSend(
        application: Application,
        server: String,
        email: String,
        accountId: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment>,
        draftId: String?
    ) {
        enqueue(
            application = application,
            type = TYPE_SEND,
            server = server,
            email = email,
            accountId = accountId,
            payload = JSONObject().apply {
                put("to", JSONArray().apply { to.forEach { put(it) } })
                put("subject", subject)
                put("body", body)
                put("draftId", draftId)
                put("attachments", JSONArray().apply {
                    attachments.forEach { attachment ->
                        put(
                            JSONObject().apply {
                                put("id", attachment.id)
                                put("filename", attachment.filename)
                                put("mime", attachment.mime)
                                put("size", attachment.size)
                                put("localFilePath", attachment.localFilePath)
                                put("isUploaded", attachment.isUploaded)
                            }
                        )
                    }
                })
            }.toString()
        )
        OfflineQueueWorker.scheduleNow(application)
    }

    suspend fun enqueueDelete(
        application: Application,
        server: String,
        email: String,
        accountId: String,
        messageId: String
    ) {
        enqueue(application, TYPE_DELETE, server, email, accountId, JSONObject().put("messageId", messageId).toString())
        OfflineQueueWorker.scheduleNow(application)
    }

    suspend fun enqueueMove(
        application: Application,
        server: String,
        email: String,
        accountId: String,
        messageId: String,
        fromMailboxId: String,
        toMailboxId: String
    ) {
        enqueue(
            application,
            TYPE_MOVE,
            server,
            email,
            accountId,
            JSONObject().apply {
                put("messageId", messageId)
                put("fromMailboxId", fromMailboxId)
                put("toMailboxId", toMailboxId)
            }.toString()
        )
        OfflineQueueWorker.scheduleNow(application)
    }

    suspend fun enqueueMarkRead(
        application: Application,
        server: String,
        email: String,
        accountId: String,
        messageId: String,
        isRead: Boolean
    ) {
        enqueue(
            application,
            TYPE_MARK_READ,
            server,
            email,
            accountId,
            JSONObject().apply {
                put("messageId", messageId)
                put("isRead", isRead)
            }.toString()
        )
        OfflineQueueWorker.scheduleNow(application)
    }

    suspend fun enqueueToggleStar(
        application: Application,
        server: String,
        email: String,
        accountId: String,
        messageId: String,
        isStarred: Boolean
    ) {
        enqueue(
            application,
            TYPE_TOGGLE_STAR,
            server,
            email,
            accountId,
            JSONObject().apply {
                put("messageId", messageId)
                put("isStarred", isStarred)
            }.toString()
        )
        OfflineQueueWorker.scheduleNow(application)
    }

    suspend fun processPending(application: Application, limit: Int = 25): OfflineQueueSummary = mutex.withLock {
        val dao = AppDatabase.getInstance(application).pendingOperationDao()
        val operations = dao.getProcessable(limit)
        var processedCount = 0
        var failedCount = 0

        operations.forEach { operation ->
            dao.updateStatus(
                id = operation.id,
                status = STATUS_RUNNING,
                attemptCount = operation.attemptCount,
                lastError = operation.lastError,
                updatedAt = System.currentTimeMillis()
            )

            when (val result = executeOperation(application, operation)) {
                is Result.Success -> {
                    dao.deleteById(operation.id)
                    processedCount++
                }
                is Result.Error -> {
                    val nextAttemptCount = operation.attemptCount + 1
                    val status = when {
                        !shouldQueue(result.exception) -> STATUS_PERMANENT_FAILED
                        nextAttemptCount >= maxAttemptsFor(operation.type) -> STATUS_PERMANENT_FAILED
                        else -> STATUS_FAILED
                    }
                    if (status == STATUS_FAILED) {
                        failedCount++
                    }
                    dao.updateStatus(
                        id = operation.id,
                        status = status,
                        attemptCount = nextAttemptCount,
                        lastError = result.exception.message,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        }

        val pendingCount = dao.getPendingCount()
        val permanentFailedCount = dao.getPermanentFailedCount()
        OfflineQueueSummary(
            processedCount = processedCount,
            failedCount = failedCount,
            pendingCount = pendingCount,
            permanentFailedCount = permanentFailedCount
        )
    }

    suspend fun getPendingCount(application: Application): Int {
        return AppDatabase.getInstance(application).pendingOperationDao().getPendingCount()
    }

    fun observeAll(application: Application) = AppDatabase.getInstance(application).pendingOperationDao().observeAll()

    suspend fun getStats(application: Application): OfflineQueueStats {
        val dao = AppDatabase.getInstance(application).pendingOperationDao()
        val pending = dao.getPendingCount()
        val failed = dao.getFailedCount()
        val permanent = dao.getPermanentFailedCount()
        return OfflineQueueStats(
            pendingCount = pending,
            failedCount = failed,
            permanentFailedCount = permanent,
            totalCount = pending + failed + permanent
        )
    }

    suspend fun remove(application: Application, id: Long) {
        val dao = AppDatabase.getInstance(application).pendingOperationDao()
        cleanupOperationFiles(dao.getById(id))
        dao.delete(id)
    }

    suspend fun retryOne(application: Application, id: Long) {
        AppDatabase.getInstance(application).pendingOperationDao().retryById(id, System.currentTimeMillis())
        OfflineQueueWorker.scheduleNow(application)
    }

    suspend fun retryAllFailed(application: Application) {
        AppDatabase.getInstance(application).pendingOperationDao().retryAllFailed(System.currentTimeMillis())
        OfflineQueueWorker.scheduleNow(application)
    }

    suspend fun clearFailed(application: Application) {
        val dao = AppDatabase.getInstance(application).pendingOperationDao()
        dao.getFailedOperations().forEach { cleanupOperationFiles(it) }
        dao.clearFailed()
    }

    fun shouldQueue(error: Throwable): Boolean {
        val root = rootCause(error)
        return root is IOException ||
            root is UnknownHostException ||
            root is SocketTimeoutException ||
            root is ConnectException ||
            root is SSLException
    }

    private suspend fun enqueue(
        application: Application,
        type: String,
        server: String,
        email: String,
        accountId: String,
        payload: String
    ) {
        val now = System.currentTimeMillis()
        AppDatabase.getInstance(application).pendingOperationDao().insert(
            PendingOperationEntity(
                type = type,
                server = server,
                email = email,
                accountId = accountId,
                payload = payload,
                status = STATUS_PENDING,
                attemptCount = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private suspend fun executeOperation(application: Application, operation: PendingOperationEntity): Result<Unit> {
        val client = MailClientFactory.create(
            application = application,
            server = operation.server,
            email = operation.email,
            accountId = operation.accountId
        )

        return when (operation.type) {
            TYPE_SEND -> executeSend(client, operation.payload, operation.email)
            TYPE_DELETE -> executeDelete(client, operation.payload)
            TYPE_MOVE -> executeMove(client, operation.payload)
            TYPE_MARK_READ -> executeMarkRead(client, operation.payload)
            TYPE_TOGGLE_STAR -> executeToggleStar(client, operation.payload)
            else -> Result.Error(IllegalArgumentException("Unsupported queue op: ${operation.type}"))
        }
    }

    private suspend fun executeSend(
        client: com.mobilemail.data.jmap.JmapApi,
        payload: String,
        fromEmail: String
    ): Result<Unit> {
        val json = JSONObject(payload)
        val repository = ComposeRepository(client)
        val to = json.getJSONArray("to").toStringList()
        val sourceAttachments = json.getJSONArray("attachments").toAttachmentList()
        val attachments = sourceAttachments
            .map { attachment ->
                if (attachment.isUploaded) {
                    attachment
                } else {
                    val bytes = OfflineAttachmentStorage.read(requireNotNull(attachment.localFilePath))
                    repository.uploadAttachment(bytes, attachment.mime, attachment.filename).fold(
                        onError = { throw it },
                        onSuccess = { uploaded -> uploaded }
                    )
                }
            }
        return repository.sendMessage(
            from = fromEmail,
            to = to,
            subject = json.optString("subject"),
            body = json.optString("body"),
            attachments = attachments,
            draftId = json.optString("draftId").takeIf { it.isNotBlank() && it != "null" }
        ).fold(
            onError = { Result.Error(it) },
            onSuccess = {
                sourceAttachments.forEach { OfflineAttachmentStorage.delete(it.localFilePath) }
                Result.Success(Unit)
            }
        )
    }

    private suspend fun executeDelete(
        client: com.mobilemail.data.jmap.JmapApi,
        payload: String
    ): Result<Unit> {
        val json = JSONObject(payload)
        return MessageActionsRepository(client).deleteMessage(json.getString("messageId")).mapUnit()
    }

    private suspend fun executeMove(
        client: com.mobilemail.data.jmap.JmapApi,
        payload: String
    ): Result<Unit> {
        val json = JSONObject(payload)
        return MessageActionsRepository(client).moveMessage(
            messageId = json.getString("messageId"),
            fromMailboxId = json.getString("fromMailboxId"),
            toMailboxId = json.getString("toMailboxId")
        ).mapUnit()
    }

    private suspend fun executeMarkRead(
        client: com.mobilemail.data.jmap.JmapApi,
        payload: String
    ): Result<Unit> {
        val json = JSONObject(payload)
        return MessageActionsRepository(client)
            .markAsRead(json.getString("messageId"), json.getBoolean("isRead"))
            .mapUnit()
    }

    private suspend fun executeToggleStar(
        client: com.mobilemail.data.jmap.JmapApi,
        payload: String
    ): Result<Unit> {
        val json = JSONObject(payload)
        return MessageActionsRepository(client)
            .toggleStarred(json.getString("messageId"), json.getBoolean("isStarred"))
            .mapUnit()
    }

    private fun Result<Boolean>.mapUnit(): Result<Unit> {
        return fold(
            onError = { Result.Error(it) },
            onSuccess = { Result.Success(Unit) }
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        return buildList {
            for (index in 0 until length()) add(getString(index))
        }
    }

    private fun JSONArray.toAttachmentList(): List<Attachment> {
        return buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                add(
                    Attachment(
                        id = item.getString("id"),
                        filename = item.getString("filename"),
                        mime = item.getString("mime"),
                        size = item.getLong("size"),
                        localFilePath = item.optString("localFilePath").takeIf { it.isNotBlank() && it != "null" },
                        isUploaded = item.optBoolean("isUploaded", true)
                    )
                )
            }
        }
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        repeat(12) {
            current = current.cause ?: return current
        }
        return current
    }

    private fun cleanupOperationFiles(operation: PendingOperationEntity?) {
        if (operation == null || operation.type != TYPE_SEND) return
        runCatching {
            val attachments = JSONObject(operation.payload).optJSONArray("attachments") ?: return
            val parsed = attachments.toAttachmentList()
            parsed.forEach { attachment ->
                if (!attachment.isUploaded) {
                    OfflineAttachmentStorage.delete(attachment.localFilePath)
                }
            }
        }
    }

    private fun maxAttemptsFor(type: String): Int = when (type) {
        TYPE_SEND -> 8
        TYPE_DELETE, TYPE_MOVE -> 6
        TYPE_MARK_READ, TYPE_TOGGLE_STAR -> 4
        else -> 4
    }
}
