package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogoutAccountUseCaseTest {
    private val useCase = LogoutAccountUseCase()

    @Test
    fun `invoke unsubscribes clears tokens and removes account`() = runTest {
        val session = SavedSession("https://mail.example.com", "user@example.com", "acc-1")
        val calls = mutableListOf<String>()
        val expectedNext = SavedSession("https://mail.example.com", "other@example.com", "acc-2")

        val result = useCase(
            session = session,
            unsubscribeTopic = { accountId ->
                calls += "unsubscribe:$accountId"
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
                "clear:https://mail.example.com:user@example.com",
                "remove:https://mail.example.com:user@example.com"
            ),
            calls
        )
        assertEquals(expectedNext, result)
    }
}
