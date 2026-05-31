package com.mobilemail.ui.messagedetail.content

internal object RemoteContentAllowanceStore {
    private val allowedMessageIds = mutableSetOf<String>()

    fun isAllowed(messageId: String): Boolean = allowedMessageIds.contains(messageId)

    fun allow(messageId: String) {
        allowedMessageIds.add(messageId)
    }
}
