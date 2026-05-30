package com.mobilemail.ui.messagedetail.content

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageDetailScrollInteropTest {

    @Test
    fun `delegates scroll to parent when webview has no internal scroll`() {
        assertEquals(false, shouldDisallowParentIntercept(MotionEvent.ACTION_DOWN, webViewHasInternalScroll = false))
        assertEquals(false, shouldDisallowParentIntercept(MotionEvent.ACTION_MOVE, webViewHasInternalScroll = false))
        assertEquals(false, shouldDisallowParentIntercept(MotionEvent.ACTION_UP, webViewHasInternalScroll = false))
    }

    @Test
    fun `captures scroll when webview can scroll internally`() {
        assertEquals(true, shouldDisallowParentIntercept(MotionEvent.ACTION_DOWN, webViewHasInternalScroll = true))
        assertEquals(true, shouldDisallowParentIntercept(MotionEvent.ACTION_MOVE, webViewHasInternalScroll = true))
        assertEquals(false, shouldDisallowParentIntercept(MotionEvent.ACTION_UP, webViewHasInternalScroll = true))
    }

    @Test
    fun `returns null for unrelated actions`() {
        assertNull(shouldDisallowParentIntercept(MotionEvent.ACTION_OUTSIDE, webViewHasInternalScroll = true))
    }
}
