package com.mobilemail.data.jmap

import android.app.Application
import com.mobilemail.data.oauth.OAuthDiscovery
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.preferences.PreferencesManager

object MailClientFactory {
    suspend fun create(
        application: Application,
        server: String,
        email: String,
        accountId: String
    ): JmapApi {
        val tokenStore = TokenStore(application)
        tokenStore.getTokens(server, email)
            ?: throw OAuthTokenExpiredException("Сессия истекла. Выполните вход заново.")

        val preferencesManager = PreferencesManager(application)
        val metadata = preferencesManager.getOAuthMetadata(server) ?: run {
            val httpClient = OAuthDiscovery.createClient()
            val discovery = OAuthDiscovery(httpClient)
            val discovered = discovery.discover(server)
            preferencesManager.saveOAuthMetadata(server, discovered)
            discovered
        }

        return JmapOAuthClient.getOrCreate(
            serverUrl = server,
            email = email,
            accountId = accountId,
            tokenStore = tokenStore,
            metadata = metadata,
            clientId = "mail-client"
        )
    }
}
