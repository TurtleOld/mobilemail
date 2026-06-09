package com.mobilemail.ui.messagedetail.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlMessageRemoteContentPolicyTest {

    @Test
    fun `uses null base url for opaque-origin message html`() {
        assertNull(HtmlMessageWebViewLoader.baseUrl)
    }

    @Test
    fun `does not use a remote image domain allowlist`() {
        assertTrue(HtmlMessageRemoteContentPolicy.remoteImageDomainAllowlist.isEmpty())
    }

    @Test
    fun `blocks all remote webview network loads until user allows remote content`() {
        val settings = HtmlMessageRemoteContentPolicy.settingsFor(blockRemoteLoads = true)

        assertEquals(RemoteImageMode.BlockAll, settings.mode)
        assertTrue(settings.blockNetworkImages)
        assertTrue(settings.blockNetworkLoads)
    }

    @Test
    fun `allows remote webview network loads after user consent`() {
        val settings = HtmlMessageRemoteContentPolicy.settingsFor(blockRemoteLoads = false)

        assertEquals(RemoteImageMode.AllowAllAfterUserConsent, settings.mode)
        assertFalse(settings.blockNetworkImages)
        assertFalse(settings.blockNetworkLoads)
    }
}
