package com.mobilemail.ui.navigation

import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.domain.port.AccountPushTopicsPort
import com.mobilemail.domain.usecase.HandleMessagesStartupUseCase
import com.mobilemail.domain.usecase.LogoutAccountUseCase
import com.mobilemail.domain.usecase.LogoutAllUseCase
import com.mobilemail.domain.usecase.ResolveMessagesViewModelContextUseCase
import com.mobilemail.domain.usecase.ResolvePushNavigationUseCase
import com.mobilemail.domain.usecase.ResolveValidSessionUseCase
import kotlinx.coroutines.CoroutineScope

data class AppNavigationDependencies(
    val database: AppDatabase,
    val preferencesManager: PreferencesManager,
    val tokenStore: TokenStore,
    val activityScope: CoroutineScope,
    val resolveValidSessionUseCase: ResolveValidSessionUseCase,
    val logoutAccountUseCase: LogoutAccountUseCase,
    val logoutAllUseCase: LogoutAllUseCase,
    val resolvePushNavigationUseCase: ResolvePushNavigationUseCase,
    val handleMessagesStartupUseCase: HandleMessagesStartupUseCase,
    val resolveMessagesViewModelContextUseCase: ResolveMessagesViewModelContextUseCase,
    val accountPushTopicsPort: AccountPushTopicsPort,
)
