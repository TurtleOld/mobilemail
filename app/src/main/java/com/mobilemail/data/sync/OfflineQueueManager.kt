package com.mobilemail.data.sync

import android.app.Application
import com.mobilemail.domain.common.fold
import com.mobilemail.domain.common.Result
import com.mobilemail.domain.common.Result.Success
import com.mobilemail.domain.common.Result.Error
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.data.jmap.SendOutcomeUnknownException
import com.mobilemail.data.local.dao.PendingOperationDao
import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.data.model.Attachment
import com.mobilemail.data.repository.ComposeRepository
import com.mobilemail.domain.model.toData
import com.mobilemail.data.repository.MessageActionsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class SendParams(
    val to: List<String>,
    val subject: String,
    val body: String,
    val attachments: List<com.mobilemail.data.model.Attachment>,
    val draftId: String?
)

data class OfflineQueueSummary(
    val processedCount: Int,
    val failedCount: Int,
    val pendingCount: Int,
    val permanentFailedCount: Int = 0,
    val unknownOutcomeCount: Int = 0
)

data class OfflineQueueStats(
    val pendingCount: Int,
    val failedCount: Int,
    val permanentFailedCount: Int,
    val totalCount: Int,
    val unknownOutcomeCount: Int = 0
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

    /** Запрос отправки уже ушёл на сервер; только для операций [TYPE_SEND]. */
    const val STATUS_SUBMITTING = "submitting"

    /** Результат отправки неизвестен: письмо нельзя отправлять повторно автоматически. */
    const val STATUS_UNKNOWN_OUTCOME = "unknown_outcome"
    const val STATUS_PERMANENT_FAILED = "permanent_failed"

    private val mutex = Mutex()

    /**
     * Ставит отправку письма в офлайн-очередь.
     *
     * @param outcomeUnknown True, когда запрос отправки мог дойти до сервера, но подтверждение
     *   потеряно: задание попадает в очередь как [STATUS_UNKNOWN_OUTCOME] и не повторяется
     *   автоматически.
     */
    suspend fun enqueueSend(
        application: Application,
        server: String,
        email: String,
        accountId: String,
        params: SendParams,
        outcomeUnknown: Boolean = false
    ) {
        enqueue(
            application = application,
            type = TYPE_SEND,
            server = server,
            email = email,
            accountId = accountId,
            payload = JSONObject().apply {
                put("to", JSONArray().apply { params.to.forEach { put(it) } })
                put("subject", params.subject)
                put("body", params.body)
                put("draftId", params.draftId)
                put("attachments", JSONArray().apply {
                    params.attachments.forEach { attachment ->
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
            }.toString(),
            status = if (outcomeUnknown) STATUS_UNKNOWN_OUTCOME else STATUS_PENDING
        )
        OfflineQueueWorker.scheduleNow(application)
    }

    @Suppress("LongParameterList")
    suspend fun enqueueSend(
        application: Application,
        server: String,
        email: String,
        accountId: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment>,
        draftId: String?,
        outcomeUnknown: Boolean = false
    ) = enqueueSend(
        application = application,
        server = server,
        email = email,
        accountId = accountId,
        params = SendParams(to = to, subject = subject, body = body, attachments = attachments, draftId = draftId),
        outcomeUnknown = outcomeUnknown
    )

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
        QueuedAttachmentCleanup.releaseLegacy(application)
        processOperations(
            dao = dao,
            limit = limit,
            releaseAttachments = { operation -> QueuedAttachmentCleanup.releaseForOperations(application, listOf(operation)) }
        ) { operation, onBeforeSubmission -> executeOperation(application, operation, onBeforeSubmission) }
    }

    /**
     * Выполняет задания очереди.
     *
     * @param execute обработчик задания; второй аргумент — точка восстановления, которую
     *   операция отправки обязана вызвать непосредственно перед запросом EmailSubmission/set.
     */
    internal suspend fun processOperations(
        dao: PendingOperationDao,
        limit: Int,
        releaseAttachments: suspend (PendingOperationEntity) -> Unit = {},
        execute: suspend (PendingOperationEntity, suspend () -> Unit) -> Result<Unit>
    ): OfflineQueueSummary {
        // No worker is running while the mutex is held, so any 'running' row was left by a killed process.
        dao.resetRunning(System.currentTimeMillis())
        // A 'submitting' row died after the EmailSubmission/set request may have reached the server,
        // so its result is unknown and it must not be resent automatically.
        dao.resetInterruptedSubmissions(System.currentTimeMillis())
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
            val onBeforeSubmission: suspend () -> Unit = {
                dao.updateStatus(
                    id = operation.id,
                    status = STATUS_SUBMITTING,
                    attemptCount = operation.attemptCount,
                    lastError = operation.lastError,
                    updatedAt = System.currentTimeMillis()
                )
            }

            val result = try {
                execute(operation, onBeforeSubmission)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.Error(error)
            }

            when (result) {
                is Result.Success -> {
                    dao.deleteById(operation.id)
                    // Копии освобождаются после удаления строки, чтобы она не считалась их потребителем.
                    releaseAttachments(operation)
                    processedCount++
                }
                is Result.Error -> {
                    val nextAttemptCount = operation.attemptCount + 1
                    val status = resolveNextStatus(
                        operationType = operation.type,
                        nextAttemptCount = nextAttemptCount,
                        error = result.exception
                    )
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
        return OfflineQueueSummary(
            processedCount = processedCount,
            failedCount = failedCount,
            pendingCount = pendingCount,
            permanentFailedCount = permanentFailedCount,
            unknownOutcomeCount = dao.getUnknownOutcomeCount()
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
        val unknownOutcome = dao.getUnknownOutcomeCount()
        return OfflineQueueStats(
            pendingCount = pending,
            failedCount = failed,
            permanentFailedCount = permanent,
            totalCount = pending + failed + permanent + unknownOutcome,
            unknownOutcomeCount = unknownOutcome
        )
    }

    suspend fun remove(application: Application, id: Long) {
        val dao = AppDatabase.getInstance(application).pendingOperationDao()
        val operation = dao.getById(id)
        dao.delete(id)
        QueuedAttachmentCleanup.releaseForOperations(application, listOfNotNull(operation))
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
        val operations = dao.getFailedOperations()
        dao.clearFailed()
        QueuedAttachmentCleanup.releaseForOperations(application, operations)
    }

    fun shouldQueue(error: Throwable): Boolean {
        val root = rootCause(error)
        return root is IOException ||
            root is UnknownHostException ||
            root is SocketTimeoutException ||
            root is ConnectException ||
            root is SSLException
    }

    /**
     * Проверяет, что отправка могла дойти до сервера, но её результат неизвестен.
     *
     * @param error ошибка операции отправки, включая обёрнутые причины.
     * @return True, когда письмо нельзя отправлять повторно без решения пользователя.
     */
    fun isSendOutcomeUnknown(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SendOutcomeUnknownException) return true
            current = current.cause
        }
        return false
    }

    private suspend fun enqueue(
        application: Application,
        type: String,
        server: String,
        email: String,
        accountId: String,
        payload: String,
        status: String = STATUS_PENDING
    ) {
        val now = System.currentTimeMillis()
        AppDatabase.getInstance(application).pendingOperationDao().insert(
            PendingOperationEntity(
                type = type,
                server = server,
                email = email,
                accountId = accountId,
                payload = payload,
                status = status,
                attemptCount = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private suspend fun executeOperation(
        application: Application,
        operation: PendingOperationEntity,
        onBeforeSubmission: suspend () -> Unit
    ): Result<Unit> {
        val client = MailClientFactory.create(
            application = application,
            server = operation.server,
            email = operation.email,
            accountId = operation.accountId
        )

        return when (operation.type) {
            TYPE_SEND -> OfflineQueueSendExecutor.execute(
                repository = ComposeRepository(client),
                payload = operation.payload,
                fromEmail = operation.email,
                onBeforeSubmission = onBeforeSubmission
            )
            TYPE_DELETE -> executeDelete(client, operation.payload)
            TYPE_MOVE -> executeMove(client, operation.payload)
            TYPE_MARK_READ -> executeMarkRead(client, operation.payload)
                .also { result ->
                    if (result is Result.Success) {
                        // Keep the cache in step with a status the server has just accepted.
                        runCatching {
                            val json = JSONObject(operation.payload)
                            AppDatabase.getInstance(application).messageDao().updateReadStatus(
                                messageId = json.getString("messageId"),
                                isUnread = !json.getBoolean("isRead")
                            )
                        }
                    }
                }
            TYPE_TOGGLE_STAR -> executeToggleStar(client, operation.payload)
            else -> Result.Error(IllegalArgumentException("Unsupported queue op: ${operation.type}"))
        }
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

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        repeat(12) {
            current = current.cause ?: return current
        }
        return current
    }

    private fun maxAttemptsFor(type: String): Int = when (type) {
        TYPE_SEND -> 8
        TYPE_DELETE, TYPE_MOVE -> 6
        TYPE_MARK_READ, TYPE_TOGGLE_STAR -> 4
        else -> 4
    }

    internal fun resolveNextStatus(
        operationType: String,
        nextAttemptCount: Int,
        error: Throwable
    ): String {
        return when {
            isSendOutcomeUnknown(error) -> STATUS_UNKNOWN_OUTCOME
            !shouldQueue(error) -> STATUS_PERMANENT_FAILED
            nextAttemptCount >= maxAttemptsFor(operationType) -> STATUS_PERMANENT_FAILED
            else -> STATUS_FAILED
        }
    }
}
