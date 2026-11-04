package com.mobilemail.data.sync

import com.mobilemail.data.local.entity.PendingOperationEntity
import org.json.JSONObject

/** Read-status intent that the offline queue has not settled with the server yet. */
private data class PendingReadStatus(
    val messageId: String,
    val isUnread: Boolean,
    val createdAt: Long
)

/**
 * Extracts queued read-status intents that may temporarily override the server value.
 *
 * Operations that already succeeded are deleted from the queue, and permanently rejected
 * operations must not hide the server state, so only pending, failed and running rows count.
 * When several intents target one message, the most recent one wins.
 */
object PendingReadStatusQueue {
    private val activeStatuses = setOf(
        OfflineQueueManager.STATUS_PENDING,
        OfflineQueueManager.STATUS_FAILED,
        OfflineQueueManager.STATUS_RUNNING
    )

    /** Returns the unread flag requested by the queue for every affected message. */
    fun unreadOverrides(operations: List<PendingOperationEntity>): Map<String, Boolean> =
        operations
            .asSequence()
            .filter { it.type == OfflineQueueManager.TYPE_MARK_READ && it.status in activeStatuses }
            .mapNotNull { it.toPendingReadStatus() }
            .sortedBy { it.createdAt }
            .associate { it.messageId to it.isUnread }

    /** Returns message ids whose local read-status intent was rejected for good. */
    fun permanentlyFailedMessageIds(operations: List<PendingOperationEntity>): Set<String> =
        operations
            .asSequence()
            .filter {
                it.type == OfflineQueueManager.TYPE_MARK_READ &&
                    it.status == OfflineQueueManager.STATUS_PERMANENT_FAILED
            }
            .mapNotNull { it.payloadMessageId() }
            .toSet()

    private fun PendingOperationEntity.toPendingReadStatus(): PendingReadStatus? = runCatching {
        val messageId = payloadMessageId() ?: return@runCatching null
        PendingReadStatus(
            messageId = messageId,
            isUnread = !JSONObject(payload).getBoolean("isRead"),
            createdAt = createdAt
        )
    }.getOrNull()

    private fun PendingOperationEntity.payloadMessageId(): String? = runCatching {
        JSONObject(payload).optString("messageId").takeIf { it.isNotBlank() }
    }.getOrNull()
}
