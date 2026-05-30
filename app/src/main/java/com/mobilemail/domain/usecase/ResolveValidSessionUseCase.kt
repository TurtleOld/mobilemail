package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession

class ResolveValidSessionUseCase {
    operator fun invoke(
        activeSession: SavedSession?,
        savedAccounts: List<SavedSession>,
        hasUsableToken: (SavedSession) -> Boolean
    ): SavedSession? {
        val activeServer = activeSession?.server
        val activeEmail = activeSession?.email
        val orderedCandidates = buildList {
            if (activeSession != null) add(activeSession)
            addAll(
                savedAccounts.filterNot {
                    it.server == activeServer && it.email == activeEmail
                }
            )
        }
        return orderedCandidates.firstOrNull(hasUsableToken)
    }
}
