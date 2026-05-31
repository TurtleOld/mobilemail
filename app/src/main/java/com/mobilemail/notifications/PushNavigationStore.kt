package com.mobilemail.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PushMessageTarget(
    val messageId: String,
    val server: String? = null,
    val email: String? = null,
    val accountId: String? = null
)

object PushNavigationStore {
    private val _pendingTarget = MutableStateFlow<PushMessageTarget?>(null)
    val pendingTarget: StateFlow<PushMessageTarget?> = _pendingTarget.asStateFlow()

    fun publish(target: PushMessageTarget?) {
        if (target != null) {
            _pendingTarget.value = target
        }
    }

    fun clear(target: PushMessageTarget? = null) {
        if (target == null || _pendingTarget.value == target) {
            _pendingTarget.value = null
        }
    }
}
