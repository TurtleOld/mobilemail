package com.mobilemail.ui.messagedetail.content

import android.net.Uri
import android.webkit.WebResourceRequest
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

    @Test
    fun `removes meta refresh declared with slash separator`() {
        val sanitized = sanitizeHtmlForWebView(
            """
            <p>Hello</p>
            <meta/http-equiv="refresh" content="0;url=https://example.com">
            """.trimIndent()
        )

        assertFalse(sanitized.contains("<meta", ignoreCase = true))
        assertFalse(sanitized.contains("example.com", ignoreCase = true))
        assertTrue(sanitized.contains("Hello"))
    }

    @Test
    fun `does not open external uri without user gesture`() {
        assertFalse(shouldOpenExternalUri(GestureWebResourceRequest(hasGesture = false)))
    }

    private class GestureWebResourceRequest(
        private val hasGesture: Boolean
    ) : WebResourceRequest {
        override fun getUrl(): Uri = error("URL must not be read for a navigation without gesture")

        override fun isForMainFrame(): Boolean = true

        override fun isRedirect(): Boolean = false

        override fun hasGesture(): Boolean = hasGesture

        override fun getMethod(): String = "GET"

        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
}
