package com.mobilemail.notifications

import com.mobilemail.domain.port.AccountPushTopicsPort

class NtfyAccountPushTopicsAdapter : AccountPushTopicsPort {
    override fun subscribe(accountId: String) {
        NtfyTopics.subscribe(accountId)
    }

    override fun unsubscribe(accountId: String) {
        NtfyTopics.unsubscribe(accountId)
    }
}
