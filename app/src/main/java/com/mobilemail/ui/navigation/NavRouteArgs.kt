package com.mobilemail.ui.navigation

import androidx.navigation.NavBackStackEntry

data class AccountRouteArgs(
    val server: String,
    val email: String,
    val accountId: String,
)

data class MessageRouteArgs(
    val server: String,
    val email: String,
    val accountId: String,
    val messageId: String,
)

data class ComposeRouteArgs(
    val server: String,
    val email: String,
    val accountId: String,
    val draftToken: String,
)

data class SettingsRouteArgs(
    val server: String,
    val email: String,
)

fun NavBackStackEntry.decodeAccountRouteArgs(): AccountRouteArgs? {
    val server = decodeRouteSegment(arguments?.getString("server") ?: return null)
    val email = decodeRouteSegment(arguments?.getString("email") ?: return null)
    val accountId = decodeRouteSegment(arguments?.getString("accountId") ?: return null)
    return AccountRouteArgs(server, email, accountId)
}

fun NavBackStackEntry.decodeMessageRouteArgs(): MessageRouteArgs? {
    val account = decodeAccountRouteArgs() ?: return null
    val messageId = decodeRouteSegment(arguments?.getString("messageId") ?: return null)
    return MessageRouteArgs(
        server = account.server,
        email = account.email,
        accountId = account.accountId,
        messageId = messageId,
    )
}

fun NavBackStackEntry.decodeComposeRouteArgs(): ComposeRouteArgs? {
    val account = decodeAccountRouteArgs() ?: return null
    val draftToken = decodeRouteSegment(arguments?.getString("draftToken") ?: "-")
    return ComposeRouteArgs(
        server = account.server,
        email = account.email,
        accountId = account.accountId,
        draftToken = draftToken,
    )
}

fun NavBackStackEntry.decodeSettingsRouteArgs(): SettingsRouteArgs? {
    val server = decodeRouteSegment(arguments?.getString("server") ?: return null)
    val email = decodeRouteSegment(arguments?.getString("email") ?: return null)
    return SettingsRouteArgs(server, email)
}
