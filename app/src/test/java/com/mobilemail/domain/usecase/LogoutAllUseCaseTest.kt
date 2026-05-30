package com.mobilemail.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogoutAllUseCaseTest {
    private val useCase = LogoutAllUseCase()

    @Test
    fun `invoke unsubscribes all accounts and clears state in order`() = runTest {
        val calls = mutableListOf<String>()
        val accountIds = listOf("acc-1", "acc-2")

        useCase(
            accountIds = accountIds,
            unsubscribeTopic = { id -> calls += "unsubscribe:$id" },
            clearAllSessions = { calls += "clearSessions" },
            clearAllTokens = { calls += "clearTokens" },
            clearJmapCaches = { calls += "clearCaches" }
        )

        assertEquals(
            listOf(
                "unsubscribe:acc-1",
                "unsubscribe:acc-2",
                "clearSessions",
                "clearTokens",
                "clearCaches"
            ),
            calls
        )
    }
}
