package com.mobilemail.ui.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MessageListBridgeCoordinatorTest {
    @Test
    fun `forwards delete move and read-status callbacks`() {
        val removed = mutableListOf<String>()
        val readUpdates = mutableListOf<Pair<String, Boolean>>()
        val coordinator = MessageListBridgeCoordinator(
            removeMessage = { removed += it },
            updateReadStatus = { id, unread -> readUpdates += id to unread }
        )

        coordinator.onMessageDeleted("m-1")
        coordinator.onMessageMoved("m-2")
        coordinator.onReadStatusChanged("m-3", true)

        assertEquals(listOf("m-1", "m-2"), removed)
        assertEquals(listOf("m-3" to true), readUpdates)
    }

    @Test
    fun `bindDetailReadStatusCallback provides bridge callback`() {
        var bound: ((String, Boolean) -> Unit)? = null
        val readUpdates = mutableListOf<Pair<String, Boolean>>()
        val coordinator = MessageListBridgeCoordinator(
            removeMessage = {},
            updateReadStatus = { id, unread -> readUpdates += id to unread }
        )

        coordinator.bindDetailReadStatusCallback { callback -> bound = callback }

        assertNotNull(bound)
        bound?.invoke("m-10", false)
        assertEquals(listOf("m-10" to false), readUpdates)
    }
}
