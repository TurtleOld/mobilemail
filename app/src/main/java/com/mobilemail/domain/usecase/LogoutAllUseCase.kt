package com.mobilemail.domain.usecase

class LogoutAllUseCase {
    suspend operator fun invoke(
        accountIds: List<String>,
        unsubscribeTopic: suspend (String) -> Unit,
        clearAllSessions: suspend () -> Unit,
        clearAllTokens: suspend () -> Unit,
        clearJmapCaches: suspend () -> Unit
    ) {
        accountIds.forEach { unsubscribeTopic(it) }
        clearAllSessions()
        clearAllTokens()
        clearJmapCaches()
    }
}
