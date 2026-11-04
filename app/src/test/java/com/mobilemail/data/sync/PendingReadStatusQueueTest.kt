package com.mobilemail.data.sync

import com.mobilemail.data.local.entity.PendingOperationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingReadStatusQueueTest {

    @Test
    fun `queued mark-read overrides the server as unread false`() {
        val overrides = PendingReadStatusQueue.unreadOverrides(
            listOf(operation(id = 1, messageId = "m-1", isRead = true, status = OfflineQueueManager.STATUS_PENDING))
        )

        assertEquals(mapOf("m-1" to false), overrides)
    }

    @Test
    fun `queued mark-unread overrides the server as unread true`() {
        val overrides = PendingReadStatusQueue.unreadOverrides(
            listOf(operation(id = 1, messageId = "m-1", isRead = false, status = OfflineQueueManager.STATUS_FAILED))
        )

        assertEquals(mapOf("m-1" to true), overrides)
    }

    @Test
    fun `permanently rejected operation does not override the server`() {
        val overrides = PendingReadStatusQueue.unreadOverrides(
            listOf(operation(id = 1, messageId = "m-1", isRead = true, status = OfflineQueueManager.STATUS_PERMANENT_FAILED))
        )

        assertEquals(emptyMap<String, Boolean>(), overrides)
    }

    @Test
    fun `operation of another type does not override read status`() {
        val overrides = PendingReadStatusQueue.unreadOverrides(
            listOf(
                operation(
                    id = 1,
                    messageId = "m-1",
                    isRead = true,
                    status = OfflineQueueManager.STATUS_PENDING,
                    type = OfflineQueueManager.TYPE_TOGGLE_STAR
                )
            )
        )

        assertEquals(emptyMap<String, Boolean>(), overrides)
    }

    @Test
    fun `newest queued intent wins for the same message`() {
        val overrides = PendingReadStatusQueue.unreadOverrides(
            listOf(
                operation(id = 1, messageId = "m-1", isRead = true, status = OfflineQueueManager.STATUS_PENDING, createdAt = 10),
                operation(id = 2, messageId = "m-1", isRead = false, status = OfflineQueueManager.STATUS_PENDING, createdAt = 20)
            )
        )

        assertEquals(mapOf("m-1" to true), overrides)
    }

    @Test
    fun `malformed payload is ignored`() {
        val overrides = PendingReadStatusQueue.unreadOverrides(
            listOf(operation(id = 1, messageId = "", isRead = true, status = OfflineQueueManager.STATUS_PENDING))
        )

        assertEquals(emptyMap<String, Boolean>(), overrides)
    }

    @Test
    fun `rejected operations are reported as settled message ids`() {
        val ids = PendingReadStatusQueue.permanentlyFailedMessageIds(
            listOf(
                operation(id = 1, messageId = "m-1", isRead = true, status = OfflineQueueManager.STATUS_PERMANENT_FAILED),
                operation(id = 2, messageId = "m-2", isRead = true, status = OfflineQueueManager.STATUS_PENDING)
            )
        )

        assertEquals(setOf("m-1"), ids)
    }

    private fun operation(
        id: Long,
        messageId: String,
        isRead: Boolean,
        status: String,
        type: String = OfflineQueueManager.TYPE_MARK_READ,
        createdAt: Long = 0
    ) = PendingOperationEntity(
        id = id,
        type = type,
        server = "https://example.test",
        email = "user@example.test",
        accountId = "acc",
        payload = """{"messageId":"$messageId","isRead":$isRead}""",
        status = status,
        attemptCount = 0,
        lastError = null,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}
