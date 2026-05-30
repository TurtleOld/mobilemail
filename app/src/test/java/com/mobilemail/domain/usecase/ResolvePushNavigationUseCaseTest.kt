package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.notifications.PushMessageTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvePushNavigationUseCaseTest {
    private val useCase = ResolvePushNavigationUseCase()

    @Test
    fun `root returns no-op on pin lock route`() {
        val action = useCase(
            ResolvePushNavigationUseCase.RootInput(
                currentRoute = "pin-lock",
                pinLockRoute = "pin-lock",
                messagesPatternRoute = "messages/{server}/{email}/{accountId}",
                target = PushMessageTarget(messageId = "m-1"),
                activeSession = null,
                savedAccounts = emptyList()
            )
        )

        assertTrue(action is ResolvePushNavigationUseCase.RootAction.NoOp)
    }

    @Test
    fun `root returns navigate when matching session exists`() {
        val current = SavedSession("https://mail.example.com", "a@example.com", "acc-a")
        val action = useCase(
            ResolvePushNavigationUseCase.RootInput(
                currentRoute = "settings/one/two",
                pinLockRoute = "pin-lock",
                messagesPatternRoute = "messages/{server}/{email}/{accountId}",
                target = PushMessageTarget(messageId = "m-1", email = "a@example.com"),
                activeSession = current,
                savedAccounts = listOf(current)
            )
        )

        assertEquals(
            ResolvePushNavigationUseCase.RootAction.NavigateToMessages(current),
            action
        )
    }

    @Test
    fun `messages returns open message when current session matches`() {
        val current = SavedSession("https://mail.example.com", "a@example.com", "acc-a")
        val action = useCase.resolveInMessages(
            ResolvePushNavigationUseCase.MessagesInput(
                target = PushMessageTarget(messageId = "m-1", email = "a@example.com"),
                currentSession = current,
                savedAccounts = listOf(current)
            )
        )

        assertEquals(ResolvePushNavigationUseCase.MessagesAction.OpenMessage("m-1"), action)
    }

    @Test
    fun `messages returns switch session when another session matches`() {
        val current = SavedSession("https://mail.example.com", "a@example.com", "acc-a")
        val other = SavedSession("https://mail.example.com", "b@example.com", "acc-b")
        val action = useCase.resolveInMessages(
            ResolvePushNavigationUseCase.MessagesInput(
                target = PushMessageTarget(messageId = "m-1", email = "b@example.com"),
                currentSession = current,
                savedAccounts = listOf(other)
            )
        )

        assertEquals(
            ResolvePushNavigationUseCase.MessagesAction.SwitchSessionAndOpenInbox(other),
            action
        )
    }
}
