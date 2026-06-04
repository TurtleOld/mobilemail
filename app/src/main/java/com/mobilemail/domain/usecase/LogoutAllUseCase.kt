package com.mobilemail.domain.usecase

class LogoutAllUseCase {
    suspend operator fun invoke(
        accountIds: List<String>,
        unsubscribeTopic: suspend (String) -> Unit,
        revokeAllTokens: suspend () -> Unit,
        clearAllSessions: suspend () -> Unit,
        clearAllTokens: suspend () -> Unit,
        clearJmapCaches: suspend () -> Unit
    ) {
        accountIds.forEach { unsubscribeTopic(it) }
        try {
            revokeAllTokens()
        } catch (_: Exception) {
            // best-effort: revocation failure must not block local session clearing
        }
        clearAllSessions()
        clearAllTokens()
        clearJmapCaches()
    }
}
