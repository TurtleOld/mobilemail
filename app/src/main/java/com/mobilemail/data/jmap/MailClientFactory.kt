package com.mobilemail.data.jmap

import android.app.Application
import com.mobilemail.data.oauth.OAuthDiscovery
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.preferences.PreferencesManager
import kotlinx.coroutines.runBlocking

object MailClientFactory {
    fun create(
        application: Application,
        server: String,
        email: String,
        accountId: String
    ): JmapApi {
        val tokenStore = TokenStore(application)
        val tokens = tokenStore.getTokens(server, email)
            ?: throw OAuthTokenExpiredException("Сессия истекла. Выполните вход заново.")

        return runBlocking {
            val preferencesManager = PreferencesManager(application)
            val metadata = preferencesManager.getOAuthMetadata(server) ?: run {
                val httpClient = OAuthDiscovery.createClient()
                val discovery = OAuthDiscovery(httpClient)
                val discovered = discovery.discover(server)
                preferencesManager.saveOAuthMetadata(server, discovered)
                discovered
            }

            JmapOAuthClient.getOrCreate(
                serverUrl = server,
                email = email,
                accountId = accountId,
                tokenStore = tokenStore,
                metadata = metadata,
                clientId = "mail-client"
            )
        }
    }
}
