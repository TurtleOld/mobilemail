package com.mobilemail.data.sync

import androidx.work.ListenableWorker
import com.mobilemail.data.oauth.StoredToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TokenRefreshWorkerTest {

    @Test
    fun `shouldRefresh returns true when token expires inside refresh window`() {
        val now = 1_000L
        val stored = StoredToken(
            accessToken = "access",
            tokenType = "Bearer",
            expiresAt = now + TimeUnit.HOURS.toMillis(TokenRefreshWorkPolicy.REFRESH_WINDOW_HOURS),
            refreshToken = "refresh"
        )

        assertTrue(TokenRefreshWorker.shouldRefresh(stored, now))
    }

    @Test
    fun `shouldRefresh returns true for expired token with refresh token`() {
        val now = 1_000L
        val stored = StoredToken(
            accessToken = "access",
            tokenType = "Bearer",
            expiresAt = now - 1,
            refreshToken = "refresh"
        )

        assertTrue(TokenRefreshWorker.shouldRefresh(stored, now))
    }

    @Test
    fun `shouldRefresh returns false without refresh token or expiry`() {
        val now = 1_000L

        assertFalse(
            TokenRefreshWorker.shouldRefresh(
                StoredToken("access", "Bearer", expiresAt = now + 1, refreshToken = null),
                now
            )
        )
        assertFalse(
            TokenRefreshWorker.shouldRefresh(
                StoredToken("access", "Bearer", expiresAt = null, refreshToken = "refresh"),
                now
            )
        )
    }

    @Test
    fun `resolveResult retries when at least one refresh fails`() {
        val result = TokenRefreshWorker.resolveResult(
            TokenRefreshSummary(refreshedCount = 1, failedCount = 1, skippedCount = 0)
        )

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `resolveResult succeeds when refreshes or skips without failures`() {
        val result = TokenRefreshWorker.resolveResult(
            TokenRefreshSummary(refreshedCount = 1, failedCount = 0, skippedCount = 1)
        )

        assertEquals(ListenableWorker.Result.success(), result)
    }
}
