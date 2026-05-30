package com.mobilemail.ui.messagedetail

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageDetailScrollInteropTest {

    @Test
    fun `shouldDisallowParentIntercept returns true for drag start and move`() {
        assertEquals(true, shouldDisallowParentIntercept(MotionEvent.ACTION_DOWN))
        assertEquals(true, shouldDisallowParentIntercept(MotionEvent.ACTION_MOVE))
    }

    @Test
    fun `shouldDisallowParentIntercept returns false for drag end and cancel`() {
        assertEquals(false, shouldDisallowParentIntercept(MotionEvent.ACTION_UP))
        assertEquals(false, shouldDisallowParentIntercept(MotionEvent.ACTION_CANCEL))
    }

    @Test
    fun `shouldDisallowParentIntercept returns null for unrelated actions`() {
        assertNull(shouldDisallowParentIntercept(MotionEvent.ACTION_OUTSIDE))
    }
}
