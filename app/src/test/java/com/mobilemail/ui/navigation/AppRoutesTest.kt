package com.mobilemail.ui.navigation

import com.mobilemail.data.preferences.SavedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoutesTest {

    @Test
    fun `route patterns stay stable`() {
        assertEquals("pin-lock", AppRoutes.PinLock)
        assertEquals("login", AppRoutes.Login)
        assertEquals("add-account", AppRoutes.AddAccount)
        assertEquals("pin-setup", AppRoutes.PinSetup)
        assertEquals("messages/{server}/{email}/{accountId}", AppRoutes.MessagesPattern)
        assertEquals("message/{server}/{email}/{accountId}/{messageId}", AppRoutes.MessagePattern)
        assertEquals("compose/{server}/{email}/{accountId}/{draftToken}", AppRoutes.ComposePattern)
        assertEquals("search/{server}/{email}/{accountId}", AppRoutes.SearchPattern)
        assertEquals("outbox/{server}/{email}/{accountId}", AppRoutes.OutboxPattern)
        assertEquals("settings/{server}/{email}", AppRoutes.SettingsPattern)
    }

    @Test
    fun `messages route encodes path segments`() {
        val session = SavedSession(
            server = "https://mail.example.com",
            email = "user@host.com",
            accountId = "acc/1"
        )

        assertEquals(
            "messages/https%3A%2F%2Fmail.example.com/user%40host.com/acc%2F1",
            AppRoutes.messages(session)
        )
    }

    @Test
    fun `message route encodes message id`() {
        val session = SavedSession("mail.example.com", "user@host.com", "acc-1")

        assertEquals(
            "message/mail.example.com/user%40host.com/acc-1/msg%2Fthread%2F42",
            AppRoutes.message(session, "msg/thread/42")
        )
    }

    @Test
    fun `compose route uses default draft token`() {
        assertEquals(
            "compose/mail.example.com/user%40host.com/acc-1/-",
            AppRoutes.compose("mail.example.com", "user@host.com", "acc-1")
        )
    }

    @Test
    fun `encodeRouteSegment percent-encodes reserved characters`() {
        assertEquals("user%40host.com", encodeRouteSegment("user@host.com"))
        assertEquals("msg%2Fthread%2F42", encodeRouteSegment("msg/thread/42"))
        assertEquals("https%3A%2F%2Fmail.example.com", encodeRouteSegment("https://mail.example.com"))
    }

    @Test
    fun `account scoped routes share encoded session segments`() {
        val server = "mail.example.com"
        val email = "user@host.com"
        val accountId = "acc-1"
        val encodedEmail = "user%40host.com"

        assertEquals(
            "search/$server/$encodedEmail/$accountId",
            AppRoutes.search(server, email, accountId)
        )
        assertEquals(
            "outbox/$server/$encodedEmail/$accountId",
            AppRoutes.outbox(server, email, accountId)
        )
        assertTrue(AppRoutes.settings(server, email).startsWith("settings/$server/$encodedEmail"))
    }
}
