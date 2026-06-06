package com.mobilemail.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogoutAllUseCaseTest {
    private val useCase = LogoutAllUseCase()

    @Test
    fun `invoke unsubscribes revokes and clears state in order`() = runTest {
        val calls = mutableListOf<String>()
        val accountIds = listOf("acc-1", "acc-2")

        useCase(LogoutAllParams(
            accountIds = accountIds,
            unsubscribeTopic = { id -> calls += "unsubscribe:$id" },
            revokeAllTokens = { calls += "revokeAll" },
            clearAllSessions = { calls += "clearSessions" },
            clearAllTokens = { calls += "clearTokens" },
            clearJmapCaches = { calls += "clearCaches" },
        ))

        assertEquals(
            listOf(
                "unsubscribe:acc-1",
                "unsubscribe:acc-2",
                "revokeAll",
                "clearSessions",
                "clearTokens",
                "clearCaches"
            ),
            calls
        )
    }

    @Test
    fun `invoke clears state even when revokeAllTokens throws`() = runTest {
        val calls = mutableListOf<String>()

        useCase(LogoutAllParams(
            accountIds = listOf("acc-1"),
            unsubscribeTopic = { calls += "unsubscribe" },
            revokeAllTokens = { throw java.io.IOException("network error") },
            clearAllSessions = { calls += "clearSessions" },
            clearAllTokens = { calls += "clearTokens" },
            clearJmapCaches = { calls += "clearCaches" },
        ))

        assertEquals(
            listOf("unsubscribe", "clearSessions", "clearTokens", "clearCaches"),
            calls
        )
    }
}
