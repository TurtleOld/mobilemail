package com.mobilemail.domain.port

interface AccountPushTopicsPort {
    fun subscribe(accountId: String)
    fun unsubscribe(accountId: String)
}
