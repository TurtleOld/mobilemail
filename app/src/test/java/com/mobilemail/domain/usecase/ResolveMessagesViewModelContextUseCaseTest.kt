package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveMessagesViewModelContextUseCaseTest {
    private val useCase = ResolveMessagesViewModelContextUseCase()

    @Test
    fun `returns stable route and key`() {
        val result = useCase(
            server = "https://mail.example.com",
            email = "user@example.com",
            accountId = "acc-1",
            buildMessagesRoute = { session: SavedSession ->
                "messages/${session.server}/${session.email}/${session.accountId}"
            }
        )

        assertEquals(
            "messages/https://mail.example.com/user@example.com/acc-1",
            result.route
        )
        assertEquals("messages_https://mail.example.com_user@example.com_acc-1", result.key)
    }
}
