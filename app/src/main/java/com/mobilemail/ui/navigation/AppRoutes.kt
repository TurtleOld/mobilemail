package com.mobilemail.ui.navigation

import android.net.Uri
import com.mobilemail.data.preferences.SavedSession

object AppRoutes {
    const val PinLock = "pin-lock"
    const val Login = "login"
    const val AddAccount = "add-account"
    const val PinSetup = "pin-setup"

    const val MessagesPattern = "messages/{server}/{email}/{accountId}"
    const val MessagePattern = "message/{server}/{email}/{accountId}/{messageId}"
    const val ComposePattern = "compose/{server}/{email}/{accountId}/{draftToken}"
    const val SearchPattern = "search/{server}/{email}/{accountId}"
    const val OutboxPattern = "outbox/{server}/{email}/{accountId}"
    const val SettingsPattern = "settings/{server}/{email}"

    fun messages(session: SavedSession): String {
        return "messages/${Uri.encode(session.server)}/${Uri.encode(session.email)}/${Uri.encode(session.accountId)}"
    }

    fun message(session: SavedSession, messageId: String): String {
        return "message/${Uri.encode(session.server)}/${Uri.encode(session.email)}/${Uri.encode(session.accountId)}/${Uri.encode(messageId)}"
    }

    fun compose(server: String, email: String, accountId: String, draftToken: String = "-"): String {
        return "compose/${Uri.encode(server)}/${Uri.encode(email)}/${Uri.encode(accountId)}/${Uri.encode(draftToken)}"
    }

    fun search(server: String, email: String, accountId: String): String {
        return "search/${Uri.encode(server)}/${Uri.encode(email)}/${Uri.encode(accountId)}"
    }

    fun outbox(server: String, email: String, accountId: String): String {
        return "outbox/${Uri.encode(server)}/${Uri.encode(email)}/${Uri.encode(accountId)}"
    }

    fun settings(server: String, email: String): String {
        return "settings/${Uri.encode(server)}/${Uri.encode(email)}"
    }
}
