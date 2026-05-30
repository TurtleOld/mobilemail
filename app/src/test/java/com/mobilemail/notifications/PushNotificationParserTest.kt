package com.mobilemail.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PushNotificationParserTest {

    @Test
    fun `fromPayloadJson parses known fields`() {
        val raw = """
            {
              "messageId": "m-123",
              "accountId": "acc-1",
              "server": "https://mail.example.com",
              "email": "user@example.com",
              "subject": "Subject",
              "fromName": "Sender"
            }
        """.trimIndent()

        val payload = PushNotificationParser.fromPayloadJson(raw)

        assertNotNull(payload)
        assertEquals("m-123", payload?.target?.messageId)
        assertEquals("acc-1", payload?.target?.accountId)
        assertEquals("https://mail.example.com", payload?.target?.server)
        assertEquals("user@example.com", payload?.target?.email)
        assertEquals("Subject", payload?.subject)
        assertEquals("Sender", payload?.fromName)
    }

    @Test
    fun `fromPayloadJson uses fallback account id`() {
        val raw = """{"messageId":"m-1"}"""

        val payload = PushNotificationParser.fromPayloadJson(raw, fallbackAccountId = "fallback-acc")

        assertNotNull(payload)
        assertEquals("fallback-acc", payload?.target?.accountId)
    }

    @Test
    fun `fromPayloadJson returns null for malformed payload`() {
        assertNull(PushNotificationParser.fromPayloadJson("not-json"))
        assertNull(PushNotificationParser.fromPayloadJson("""{"subject":"without id"}"""))
        assertNull(PushNotificationParser.fromPayloadJson(null))
    }
}
