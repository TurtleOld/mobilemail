package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.notifications.PushMessageTarget

class ResolvePushNavigationUseCase {
    data class RootInput(
        val currentRoute: String?,
        val pinLockRoute: String,
        val messagesPatternRoute: String,
        val target: PushMessageTarget,
        val activeSession: SavedSession?,
        val savedAccounts: List<SavedSession>
    )

    sealed class RootAction {
        data object NoOp : RootAction()
        data class NavigateToMessages(val session: SavedSession) : RootAction()
    }

    data class MessagesInput(
        val target: PushMessageTarget,
        val currentSession: SavedSession,
        val savedAccounts: List<SavedSession>
    )

    sealed class MessagesAction {
        data class SwitchSessionAndOpenInbox(val session: SavedSession) : MessagesAction()
        data class OpenMessage(val messageId: String) : MessagesAction()
    }

    operator fun invoke(input: RootInput): RootAction {
        if (input.currentRoute == null ||
            input.currentRoute == input.pinLockRoute ||
            input.currentRoute == input.messagesPatternRoute
        ) {
            return RootAction.NoOp
        }

        val resolvedSession = resolvePushSessionUseCase(
            target = input.target,
            currentSession = input.activeSession,
            savedAccounts = input.savedAccounts
        ) ?: return RootAction.NoOp

        return RootAction.NavigateToMessages(resolvedSession)
    }

    fun resolveInMessages(input: MessagesInput): MessagesAction {
        val resolvedSession = resolvePushSessionUseCase(
            target = input.target,
            currentSession = input.currentSession,
            savedAccounts = input.savedAccounts
        ) ?: return MessagesAction.OpenMessage(input.target.messageId)

        return if (resolvedSession != input.currentSession) {
            MessagesAction.SwitchSessionAndOpenInbox(resolvedSession)
        } else {
            MessagesAction.OpenMessage(input.target.messageId)
        }
    }

    private companion object {
        val resolvePushSessionUseCase = ResolvePushSessionUseCase()
    }
}
