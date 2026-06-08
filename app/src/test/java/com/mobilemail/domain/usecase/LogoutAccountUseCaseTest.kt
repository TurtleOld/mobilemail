package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogoutAccountUseCaseTest {
    private val useCase = LogoutAccountUseCase()

    @Test
    fun `invoke unsubscribes revokes clears tokens and removes account in order`() = runTest {
        val session = SavedSession("https://mail.example.com", "user@example.com", "acc-1")
        val calls = mutableListOf<String>()
        val expectedNext = SavedSession("https://mail.example.com", "other@example.com", "acc-2")

        val result = useCase(
            session = session,
            unsubscribeTopic = { accountId ->
                calls += "unsubscribe:$accountId"
            },
            revokeTokens = { server, email ->
                calls += "revoke:$server:$email"
            },
            clearTokens = { server, email ->
                calls += "clear:$server:$email"
            },
            removeSavedAccount = { server, email ->
                calls += "remove:$server:$email"
                expectedNext
            }
        )

        assertEquals(
            listOf(
                "unsubscribe:acc-1",
                "revoke:https://mail.example.com:user@example.com",
                "clear:https://mail.example.com:user@example.com",
                "remove:https://mail.example.com:user@example.com"
            ),
            calls
        )
        assertEquals(expectedNext, result)
    }

    @Test
    fun `invoke clears tokens even when revokeTokens throws`() = runTest {
        val session = SavedSession("https://mail.example.com", "user@example.com", "acc-1")
        val calls = mutableListOf<String>()

        useCase(
            session = session,
            unsubscribeTopic = { calls += "unsubscribe" },
            revokeTokens = { _, _ -> throw java.io.IOException("network error") },
            clearTokens = { _, _ -> calls += "clear" },
            removeSavedAccount = { _, _ -> null }
        )

        // revocation failure must not prevent token clearing
        assertEquals(listOf("unsubscribe", "clear"), calls)
    }

    @Test
    fun `invoke clears local cache when preference is enabled`() = runTest {
        val session = SavedSession("https://mail.example.com", "user@example.com", "acc-1")
        val calls = mutableListOf<String>()

        useCase(
            session = session,
            unsubscribeTopic = { calls += "unsubscribe" },
            revokeTokens = { _, _ -> calls += "revoke" },
            clearTokens = { _, _ -> calls += "clearTokens" },
            removeSavedAccount = { _, _ ->
                calls += "remove"
                null
            },
            clearLocalCache = { accountId -> calls += "clearCache:$accountId" },
            shouldClearLocalCache = { true }
        )

        assertEquals(
            listOf("unsubscribe", "revoke", "clearTokens", "clearCache:acc-1", "remove"),
            calls
        )
    }
}
