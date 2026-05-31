package com.mobilemail.domain.usecase

class HandleMessagesStartupUseCase {
    sealed class Action {
        data object NoPermissionRequest : Action()
        data object RequestNotificationPermission : Action()
    }

    suspend operator fun invoke(
        accountId: String,
        sdkInt: Int,
        tiramisuSdkInt: Int,
        alreadyRequestedPermission: Boolean,
        processPending: suspend () -> Unit,
        subscribeToTopic: suspend (String) -> Unit,
        markPermissionRequested: suspend () -> Unit
    ): Action {
        processPending()
        subscribeToTopic(accountId)

        if (alreadyRequestedPermission) {
            return Action.NoPermissionRequest
        }

        markPermissionRequested()
        return if (sdkInt >= tiramisuSdkInt) {
            Action.RequestNotificationPermission
        } else {
            Action.NoPermissionRequest
        }
    }
}
