package com.mobilemail.ui.login

import java.util.concurrent.atomic.AtomicBoolean

object OAuthBrowserSession {
    private val active = AtomicBoolean(false)

    fun markActive() {
        active.set(true)
    }

    fun clear() {
        active.set(false)
    }

    fun isActive(): Boolean = active.get()
}
