package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.notifications.PushMessageTarget

class ResolvePushSessionUseCase {
    operator fun invoke(
        target: PushMessageTarget,
        currentSession: SavedSession?,
        savedAccounts: List<SavedSession>
    ): SavedSession? {
        currentSession?.takeIf { matches(target, it) }?.let { return it }
        savedAccounts.firstOrNull { matches(target, it) }?.let { return it }
        return currentSession ?: savedAccounts.lastOrNull()
    }

    private fun matches(target: PushMessageTarget, session: SavedSession): Boolean {
        return (target.server == null || target.server == session.server) &&
            (target.email == null || target.email == session.email) &&
            (target.accountId == null || target.accountId == session.accountId)
    }
}
