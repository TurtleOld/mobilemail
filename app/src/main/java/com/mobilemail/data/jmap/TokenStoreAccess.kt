package com.mobilemail.data.jmap

import com.mobilemail.data.oauth.StoredToken
import com.mobilemail.data.oauth.TokenResponse
import com.mobilemail.data.oauth.TokenStore

interface TokenStoreAccess {
    fun getTokens(server: String, email: String): StoredToken?
    fun saveTokens(server: String, email: String, tokenResponse: TokenResponse)
    fun clearTokens(server: String, email: String)
}

class AndroidTokenStoreAccess(
    private val tokenStore: TokenStore
) : TokenStoreAccess {
    override fun getTokens(server: String, email: String): StoredToken? {
        return tokenStore.getTokens(server, email)
    }

    override fun saveTokens(server: String, email: String, tokenResponse: TokenResponse) {
        tokenStore.saveTokens(server, email, tokenResponse)
    }

    override fun clearTokens(server: String, email: String) {
        tokenStore.clearTokens(server, email)
    }
}
