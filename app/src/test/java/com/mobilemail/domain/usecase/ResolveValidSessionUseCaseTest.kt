package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveValidSessionUseCaseTest {
    private val useCase = ResolveValidSessionUseCase()

    @Test
    fun `returns active session when token is usable`() {
        val active = SavedSession("https://mail.example.com", "active@example.com", "acc-active")
        val other = SavedSession("https://mail.example.com", "other@example.com", "acc-other")

        val resolved = useCase(active, listOf(other)) { it.email == "active@example.com" }

        assertEquals(active, resolved)
    }

    @Test
    fun `falls back to saved account when active token is not usable`() {
        val active = SavedSession("https://mail.example.com", "active@example.com", "acc-active")
        val other = SavedSession("https://mail.example.com", "other@example.com", "acc-other")

        val resolved = useCase(active, listOf(other)) { it.email == "other@example.com" }

        assertEquals(other, resolved)
    }

    @Test
    fun `returns null when no account has usable token`() {
        val active = SavedSession("https://mail.example.com", "active@example.com", "acc-active")
        val other = SavedSession("https://mail.example.com", "other@example.com", "acc-other")

        val resolved = useCase(active, listOf(other)) { false }

        assertNull(resolved)
    }
}
