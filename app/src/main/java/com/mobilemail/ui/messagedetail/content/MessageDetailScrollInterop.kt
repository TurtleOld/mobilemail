package com.mobilemail.ui.messagedetail.content

import android.view.MotionEvent
import android.webkit.WebView

internal fun shouldDisallowParentIntercept(
    actionMasked: Int,
    webViewHasInternalScroll: Boolean,
): Boolean? {
    if (!webViewHasInternalScroll) {
        // WebView height matches content: parent Column should own vertical scrolling.
        return when (actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> false
            else -> null
        }
    }
    return when (actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_MOVE -> true
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> false
        else -> null
    }
}

internal fun WebView.hasInternalVerticalScroll(): Boolean {
    return canScrollVertically(1) || canScrollVertically(-1)
}

internal fun WebView.applyParentScrollHandoff() {
    setOnTouchListener { _, event ->
        val disallow = shouldDisallowParentIntercept(
            actionMasked = event.actionMasked,
            webViewHasInternalScroll = hasInternalVerticalScroll()
        )
        disallow?.let { parent?.requestDisallowInterceptTouchEvent(it) }
        false
    }
}
