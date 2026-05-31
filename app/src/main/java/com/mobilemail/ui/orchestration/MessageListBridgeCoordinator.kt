package com.mobilemail.ui.orchestration

class MessageListBridgeCoordinator(
    private val removeMessage: (String) -> Unit,
    private val updateReadStatus: (String, Boolean) -> Unit
) {
    fun onMessageDeleted(messageId: String) {
        removeMessage(messageId)
    }

    fun onMessageMoved(messageId: String) {
        removeMessage(messageId)
    }

    fun onReadStatusChanged(messageId: String, isUnread: Boolean) {
        updateReadStatus(messageId, isUnread)
    }

    fun bindDetailReadStatusCallback(
        setCallback: (((String, Boolean) -> Unit)?) -> Unit
    ) {
        setCallback(::onReadStatusChanged)
    }
}
