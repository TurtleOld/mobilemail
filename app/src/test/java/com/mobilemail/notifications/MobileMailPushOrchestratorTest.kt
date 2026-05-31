package com.mobilemail.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileMailPushOrchestratorTest {

    @Test
    fun `resolvePendingPayloads uses topic fallback account id and skips malformed`() {
        val topic = NtfyTopics.forAccount("acc-1")
        val envelopes = listOf(
            NtfyEnvelope(
                id = "1",
                event = "message",
                topic = topic,
                title = "Fallback title",
                message = """{"messageId":"m-1","subject":"Hello"}"""
            ),
            NtfyEnvelope(
                id = "2",
                event = "message",
                topic = topic,
                title = "Bad payload",
                message = """{"subject":"without id"}"""
            )
        )

        val resolved = MobileMailPushOrchestrator.resolvePendingPayloads(topic, envelopes)

        assertEquals(1, resolved.size)
        assertEquals("m-1", resolved.first().payload.target.messageId)
        assertEquals("acc-1", resolved.first().payload.target.accountId)
        assertEquals("Fallback title", resolved.first().fallbackTitle)
    }

    @Test
    fun `accountIdsForResubscribe trims filters blanks and deduplicates`() {
        val rawIds = listOf("acc-1", " acc-1 ", " ", "", "acc-2")

        val accountIds = MobileMailPushOrchestrator.accountIdsForResubscribe(rawIds)

        assertEquals(2, accountIds.size)
        assertTrue(accountIds.contains("acc-1"))
        assertTrue(accountIds.contains("acc-2"))
    }
}
