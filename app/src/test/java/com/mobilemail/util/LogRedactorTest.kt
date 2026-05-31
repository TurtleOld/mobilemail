package com.mobilemail.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {

    @Test
    fun `redact removes token style key value pairs`() {
        val source = """{"access_token":"access-123","refresh_token":"refresh-456","device_code":"device-789"}"""

        val redacted = LogRedactor.redact(source)

        assertFalse(redacted.contains("access-123"))
        assertFalse(redacted.contains("refresh-456"))
        assertFalse(redacted.contains("device-789"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun `redact removes bearer tokens and email addresses`() {
        val source = "Authorization: Bearer secret-token for user@example.com"

        val redacted = LogRedactor.redact(source)

        assertFalse(redacted.contains("secret-token"))
        assertFalse(redacted.contains("user@example.com"))
        assertTrue(redacted.contains("Bearer [REDACTED]"))
        assertTrue(redacted.contains("[EMAIL_REDACTED]"))
    }
}
