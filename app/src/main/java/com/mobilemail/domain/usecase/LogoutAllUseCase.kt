package com.mobilemail.domain.usecase

data class LogoutAllParams(
    val accountIds: List<String>,
    val unsubscribeTopic: suspend (String) -> Unit,
    val revokeAllTokens: suspend () -> Unit,
    val clearAllSessions: suspend () -> Unit,
    val clearAllTokens: suspend () -> Unit,
    val clearJmapCaches: suspend () -> Unit,
    val clearPin: suspend () -> Unit = {},
    val clearDatabase: suspend () -> Unit = {},
)

class LogoutAllUseCase {
    suspend operator fun invoke(params: LogoutAllParams) {
        params.accountIds.forEach { params.unsubscribeTopic(it) }
        try {
            params.revokeAllTokens()
        } catch (_: Exception) {
            // best-effort: revocation failure must not block local session clearing
        }
        params.clearAllSessions()
        params.clearAllTokens()
        params.clearJmapCaches()
        params.clearPin()
        params.clearDatabase()
    }
}
