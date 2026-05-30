package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession

class ResolveMessagesViewModelContextUseCase {
    data class Result(
        val route: String,
        val key: String
    )

    operator fun invoke(
        server: String,
        email: String,
        accountId: String,
        buildMessagesRoute: (SavedSession) -> String
    ): Result {
        val session = SavedSession(server, email, accountId)
        return Result(
            route = buildMessagesRoute(session),
            key = "messages_${server}_${email}_${accountId}"
        )
    }
}
