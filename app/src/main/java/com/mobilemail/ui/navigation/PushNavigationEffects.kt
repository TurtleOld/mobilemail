package com.mobilemail.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.domain.usecase.ResolvePushNavigationUseCase
import com.mobilemail.notifications.PushMessageTarget

@Composable
internal fun RootPushNavigationEffect(
    navController: NavHostController,
    currentRoute: String?,
    pendingPushTarget: PushMessageTarget?,
    preferencesManager: PreferencesManager,
    resolvePushNavigationUseCase: ResolvePushNavigationUseCase,
) {
    LaunchedEffect(pendingPushTarget, currentRoute) {
        val target = pendingPushTarget ?: return@LaunchedEffect
        val activeSession = preferencesManager.getSavedSession()
        val savedAccounts = preferencesManager.getSavedAccounts()
        when (
            val action = resolvePushNavigationUseCase(
                ResolvePushNavigationUseCase.RootInput(
                    currentRoute = currentRoute,
                    pinLockRoute = AppRoutes.PinLock,
                    messagesPatternRoute = AppRoutes.MessagesPattern,
                    target = target,
                    activeSession = activeSession,
                    savedAccounts = savedAccounts
                )
            )
        ) {
            ResolvePushNavigationUseCase.RootAction.NoOp -> Unit
            is ResolvePushNavigationUseCase.RootAction.NavigateToMessages -> {
                if (activeSession != action.session) {
                    preferencesManager.setActiveSession(action.session)
                }
                navController.navigate(AppRoutes.messages(action.session)) {
                    launchSingleTop = true
                }
            }
        }
    }
}
