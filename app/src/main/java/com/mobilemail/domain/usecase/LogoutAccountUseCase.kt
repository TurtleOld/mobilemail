package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession

class LogoutAccountUseCase {
    suspend operator fun invoke(
        session: SavedSession,
        unsubscribeTopic: suspend (String) -> Unit,
        revokeTokens: suspend (String, String) -> Unit,
        clearTokens: suspend (String, String) -> Unit,
        removeSavedAccount: suspend (String, String) -> SavedSession?
    ): SavedSession? {
        unsubscribeTopic(session.accountId)
        try {
            revokeTokens(session.server, session.email)
        } catch (_: Exception) {
            // best-effort: revocation failure must not block local token clearing
        }
        clearTokens(session.server, session.email)
        return removeSavedAccount(session.server, session.email)
    }
}
