package com.mobilemail.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mobilemail.BuildConfig
import com.mobilemail.data.oauth.OAuthDiscovery
import com.mobilemail.data.oauth.OAuthException
import com.mobilemail.data.oauth.OAuthTokenRefresh
import com.mobilemail.data.oauth.StoredToken
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.preferences.SavedSession
import java.util.concurrent.TimeUnit

data class TokenRefreshSummary(
    val refreshedCount: Int,
    val failedCount: Int,
    val skippedCount: Int
)

class TokenRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val summary = refreshSavedAccounts(applicationContext)
        return resolveResult(summary)
    }

    private suspend fun refreshSavedAccounts(context: Context): TokenRefreshSummary {
        val appContext = context.applicationContext
        val preferencesManager = PreferencesManager(appContext)
        val tokenStore = TokenStore(appContext)
        return preferencesManager.getSavedAccounts()
            .fold(TokenRefreshSummary(refreshedCount = 0, failedCount = 0, skippedCount = 0)) { summary, session ->
                summary + refreshSession(preferencesManager, tokenStore, session)
            }
    }

    private suspend fun refreshSession(
        preferencesManager: PreferencesManager,
        tokenStore: TokenStore,
        session: SavedSession
    ): TokenRefreshSummary {
        val stored = tokenStore.getTokens(session.server, session.email)
        if (!shouldRefresh(stored, System.currentTimeMillis())) {
            return TokenRefreshSummary(refreshedCount = 0, failedCount = 0, skippedCount = 1)
        }

        val refreshToken = stored?.refreshToken
            ?: return TokenRefreshSummary(refreshedCount = 0, failedCount = 0, skippedCount = 1)

        return try {
            val metadata = preferencesManager.getOAuthMetadata(session.server)
                ?: discoverAndCacheMetadata(preferencesManager, session.server)
            val refreshedToken = OAuthTokenRefresh(
                metadata = metadata,
                clientId = BuildConfig.OAUTH_CLIENT_ID,
                client = OAuthTokenRefresh.createClient()
            ).refreshToken(refreshToken)

            tokenStore.saveTokens(session.server, session.email, refreshedToken)
            TokenRefreshSummary(refreshedCount = 1, failedCount = 0, skippedCount = 0)
        } catch (error: OAuthException) {
            handleOAuthRefreshError(tokenStore, session, error)
        } catch (error: Exception) {
            Log.e("TokenRefreshWorker", "Failed to refresh OAuth token", error)
            TokenRefreshSummary(refreshedCount = 0, failedCount = 1, skippedCount = 0)
        }
    }

    private suspend fun discoverAndCacheMetadata(
        preferencesManager: PreferencesManager,
        server: String
    ): com.mobilemail.data.oauth.OAuthServerMetadata {
        val metadata = OAuthDiscovery(OAuthDiscovery.createClient()).discover(server)
        preferencesManager.saveOAuthMetadata(server, metadata)
        return metadata
    }

    private fun handleOAuthRefreshError(
        tokenStore: TokenStore,
        session: SavedSession,
        error: OAuthException
    ): TokenRefreshSummary {
        Log.e("TokenRefreshWorker", "Failed to refresh OAuth token", error)
        if (error.statusCode in 400..499) {
            tokenStore.clearTokens(session.server, session.email)
            return TokenRefreshSummary(refreshedCount = 0, failedCount = 0, skippedCount = 1)
        }
        return TokenRefreshSummary(refreshedCount = 0, failedCount = 1, skippedCount = 0)
    }

    companion object {
        private val REFRESH_WINDOW_MILLIS = TimeUnit.HOURS.toMillis(TokenRefreshWorkPolicy.REFRESH_WINDOW_HOURS)

        internal fun shouldRefresh(stored: StoredToken?, nowMillis: Long): Boolean {
            val expiresAt = stored?.expiresAt ?: return false
            return stored.refreshToken != null && expiresAt - nowMillis <= REFRESH_WINDOW_MILLIS
        }

        internal fun resolveResult(summary: TokenRefreshSummary): Result {
            return if (summary.failedCount > 0) Result.retry() else Result.success()
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TokenRefreshWorkPolicy.UNIQUE_PERIODIC,
                TokenRefreshWorkPolicy.periodicExistingWorkPolicy(),
                TokenRefreshWorkPolicy.buildPeriodicWorkRequest()
            )
        }
    }
}

private operator fun TokenRefreshSummary.plus(other: TokenRefreshSummary): TokenRefreshSummary {
    return TokenRefreshSummary(
        refreshedCount = refreshedCount + other.refreshedCount,
        failedCount = failedCount + other.failedCount,
        skippedCount = skippedCount + other.skippedCount
    )
}
