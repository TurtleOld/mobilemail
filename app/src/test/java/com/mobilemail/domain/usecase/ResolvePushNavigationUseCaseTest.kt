package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.notifications.PushMessageTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvePushNavigationUseCaseTest {
    private val useCase = ResolvePushNavigationUseCase()
    private val current = session("current@example.com", "current")
    private val other = session("other@example.com", "other")

    @Test
    fun `root returns no-op while app is locked`() {
        val action = useCase(
            ResolvePushNavigationUseCase.RootInput(
                currentRoute = "pin-lock",
                pinLockRoute = "pin-lock",
                messagesPatternRoute = "messages/{server}/{email}/{accountId}",
                target = PushMessageTarget(messageId = "m-1", accountId = other.accountId),
                activeSession = current,
                savedAccounts = listOf(other)
            )
        )

        assertTrue(action is ResolvePushNavigationUseCase.RootAction.NoOp)
    }

    @Test
    fun `root navigates to messages for resolved push session from non-message route`() {
        val action = useCase(
            ResolvePushNavigationUseCase.RootInput(
                currentRoute = "settings/{server}/{email}",
                pinLockRoute = "pin-lock",
                messagesPatternRoute = "messages/{server}/{email}/{accountId}",
                target = PushMessageTarget(messageId = "m-1", accountId = other.accountId),
                activeSession = current,
                savedAccounts = listOf(other)
            )
        )

        assertEquals(
            ResolvePushNavigationUseCase.RootAction.NavigateToMessages(other),
            action
        )
    }

    @Test
    fun `messages action switches session when push belongs to another account`() {
        val action = useCase.resolveInMessages(
            ResolvePushNavigationUseCase.MessagesInput(
                target = PushMessageTarget(messageId = "m-1", accountId = other.accountId),
                currentSession = current,
                savedAccounts = listOf(other)
            )
        )

        assertEquals(
            ResolvePushNavigationUseCase.MessagesAction.SwitchSessionAndOpenInbox(other),
            action
        )
    }

    @Test
    fun `messages action opens message when push belongs to current account`() {
        val action = useCase.resolveInMessages(
            ResolvePushNavigationUseCase.MessagesInput(
                target = PushMessageTarget(messageId = "m-1", accountId = current.accountId),
                currentSession = current,
                savedAccounts = listOf(other)
            )
        )

        assertEquals(
            ResolvePushNavigationUseCase.MessagesAction.OpenMessage("m-1"),
            action
        )
    }

    private fun session(email: String, accountId: String): SavedSession {
        return SavedSession(
            server = "https://mail.example.com",
            email = email,
            accountId = accountId
        )
    }
}
