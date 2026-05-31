package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.notifications.PushMessageTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolvePushSessionUseCaseTest {
    private val useCase = ResolvePushSessionUseCase()

    @Test
    fun `prefers current session when it matches target`() {
        val current = session("current@example.com", "current")
        val saved = session("saved@example.com", "saved")

        val result = useCase(
            target = PushMessageTarget(
                messageId = "m-1",
                server = current.server,
                email = current.email,
                accountId = current.accountId
            ),
            currentSession = current,
            savedAccounts = listOf(saved)
        )

        assertEquals(current, result)
    }

    @Test
    fun `uses matching saved account when current session does not match`() {
        val current = session("current@example.com", "current")
        val matching = session("target@example.com", "target")

        val result = useCase(
            target = PushMessageTarget(messageId = "m-1", accountId = matching.accountId),
            currentSession = current,
            savedAccounts = listOf(matching)
        )

        assertEquals(matching, result)
    }

    @Test
    fun `falls back to current session when target is not tied to a saved account`() {
        val current = session("current@example.com", "current")

        val result = useCase(
            target = PushMessageTarget(messageId = "m-1", accountId = "missing"),
            currentSession = current,
            savedAccounts = emptyList()
        )

        assertEquals(current, result)
    }

    @Test
    fun `falls back to last saved account when no current session exists`() {
        val first = session("first@example.com", "first")
        val last = session("last@example.com", "last")

        val result = useCase(
            target = PushMessageTarget(messageId = "m-1", accountId = "missing"),
            currentSession = null,
            savedAccounts = listOf(first, last)
        )

        assertEquals(last, result)
    }

    @Test
    fun `returns null when no current or saved sessions exist`() {
        val result = useCase(
            target = PushMessageTarget(messageId = "m-1"),
            currentSession = null,
            savedAccounts = emptyList()
        )

        assertNull(result)
    }

    private fun session(email: String, accountId: String): SavedSession {
        return SavedSession(
            server = "https://mail.example.com",
            email = email,
            accountId = accountId
        )
    }
}
