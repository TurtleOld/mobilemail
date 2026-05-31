package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession

class LogoutAccountUseCase {
    suspend operator fun invoke(
        session: SavedSession,
        unsubscribeTopic: suspend (String) -> Unit,
        clearTokens: suspend (String, String) -> Unit,
        removeSavedAccount: suspend (String, String) -> SavedSession?
    ): SavedSession? {
        unsubscribeTopic(session.accountId)
        clearTokens(session.server, session.email)
        return removeSavedAccount(session.server, session.email)
    }
}
