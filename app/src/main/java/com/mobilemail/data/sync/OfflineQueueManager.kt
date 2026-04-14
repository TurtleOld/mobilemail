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
    val pendingCount: Int
)

object OfflineQueueManager {
    private const val TYPE_SEND = "send"
    private const val TYPE_DELETE = "delete"
    private const val TYPE_MOVE = "move"
    private const val STATUS_PENDING = "pending"
    private const val STATUS_FAILED = "failed"
    private const val STATUS_RUNNING = "running"

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
                    failedCount++
                    dao.updateStatus(
                        id = operation.id,
                        status = STATUS_FAILED,
                        attemptCount = operation.attemptCount + 1,
                        lastError = result.exception.message,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        }

        OfflineQueueSummary(
            processedCount = processedCount,
            failedCount = failedCount,
            pendingCount = dao.getPendingCount()
        )
    }

    suspend fun getPendingCount(application: Application): Int {
        return AppDatabase.getInstance(application).pendingOperationDao().getPendingCount()
    }

    fun observeAll(application: Application) = AppDatabase.getInstance(application).pendingOperationDao().observeAll()

    suspend fun remove(application: Application, id: Long) {
        AppDatabase.getInstance(application).pendingOperationDao().delete(id)
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
}
