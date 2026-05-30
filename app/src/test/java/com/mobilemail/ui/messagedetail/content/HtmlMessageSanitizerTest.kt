package com.mobilemail.ui.messagedetail.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlMessageSanitizerTest {

    @Test
    fun `removes script tags and javascript hrefs`() {
        val sanitized = sanitizeHtmlForWebView(
            """
            <p>Hello</p>
            <script>alert(1)</script>
            <a href="javascript:alert(1)">bad</a>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
        assertTrue(sanitized.contains("Hello"))
    }
}
