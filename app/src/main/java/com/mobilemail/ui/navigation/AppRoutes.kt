package com.mobilemail.ui.navigation

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
        return "messages/${encodeRouteSegment(session.server)}/${encodeRouteSegment(session.email)}/${encodeRouteSegment(session.accountId)}"
    }

    fun message(session: SavedSession, messageId: String): String {
        return "message/${encodeRouteSegment(session.server)}/${encodeRouteSegment(session.email)}/${encodeRouteSegment(session.accountId)}/${encodeRouteSegment(messageId)}"
    }

    fun compose(server: String, email: String, accountId: String, draftToken: String = "-"): String {
        return "compose/${encodeRouteSegment(server)}/${encodeRouteSegment(email)}/${encodeRouteSegment(accountId)}/${encodeRouteSegment(draftToken)}"
    }

    fun search(server: String, email: String, accountId: String): String {
        return "search/${encodeRouteSegment(server)}/${encodeRouteSegment(email)}/${encodeRouteSegment(accountId)}"
    }

    fun outbox(server: String, email: String, accountId: String): String {
        return "outbox/${encodeRouteSegment(server)}/${encodeRouteSegment(email)}/${encodeRouteSegment(accountId)}"
    }

    fun settings(server: String, email: String): String {
        return "settings/${encodeRouteSegment(server)}/${encodeRouteSegment(email)}"
    }
}

internal fun encodeRouteSegment(value: String): String = buildString {
    value.forEach { char ->
        if (char.isLetterOrDigit() || char in "-._~") {
            append(char)
        } else {
            char.toString().toByteArray(Charsets.UTF_8).forEach { byte ->
                append('%')
                append(HEX_DIGITS[(byte.toInt() shr 4) and 0x0F])
                append(HEX_DIGITS[byte.toInt() and 0x0F])
            }
        }
    }
}

internal fun decodeRouteSegment(encoded: String): String = buildString {
    val pendingBytes = mutableListOf<Byte>()
    fun flushBytes() {
        if (pendingBytes.isNotEmpty()) {
            append(pendingBytes.toByteArray().toString(Charsets.UTF_8))
            pendingBytes.clear()
        }
    }
    var index = 0
    while (index < encoded.length) {
        if (encoded[index] == '%' && index + 2 < encoded.length) {
            pendingBytes.add(encoded.substring(index + 1, index + 3).toInt(16).toByte())
            index += 3
        } else {
            flushBytes()
            append(encoded[index])
            index++
        }
    }
    flushBytes()
}

private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
