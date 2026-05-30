package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveValidSessionUseCaseTest {
    private val useCase = ResolveValidSessionUseCase()

    @Test
    fun `returns active session when it has usable token`() {
        val active = session("active@example.com")
        val fallback = session("fallback@example.com")

        val result = useCase(
            activeSession = active,
            savedAccounts = listOf(fallback),
            hasUsableToken = { it == active }
        )

        assertEquals(active, result)
    }

    @Test
    fun `falls back to first saved account with usable token`() {
        val active = session("active@example.com")
        val invalid = session("invalid@example.com")
        val valid = session("valid@example.com")

        val result = useCase(
            activeSession = active,
            savedAccounts = listOf(invalid, valid),
            hasUsableToken = { it == valid }
        )

        assertEquals(valid, result)
    }

    @Test
    fun `does not evaluate duplicated active session twice`() {
        val active = session("active@example.com")
        val checked = mutableListOf<SavedSession>()

        useCase(
            activeSession = active,
            savedAccounts = listOf(active),
            hasUsableToken = {
                checked.add(it)
                false
            }
        )

        assertEquals(listOf(active), checked)
    }

    @Test
    fun `returns null when no candidate has usable token`() {
        val result = useCase(
            activeSession = session("active@example.com"),
            savedAccounts = listOf(session("fallback@example.com")),
            hasUsableToken = { false }
        )

        assertNull(result)
    }

    private fun session(email: String): SavedSession {
        return SavedSession(
            server = "https://mail.example.com",
            email = email,
            accountId = email.substringBefore("@")
        )
    }
}
