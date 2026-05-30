package com.mobilemail.ui.navigation

import org.junit.Assert.assertEquals
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
}
