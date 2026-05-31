package com.mobilemail.ui.navigation

import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import com.mobilemail.data.jmap.JmapOAuthClient
import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.model.MessageDetail
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.data.sync.OfflineQueueManager
import com.mobilemail.domain.usecase.HandleMessagesStartupUseCase
import com.mobilemail.domain.usecase.LogoutAccountUseCase
import com.mobilemail.domain.usecase.LogoutAllUseCase
import com.mobilemail.domain.usecase.ResolveMessagesViewModelContextUseCase
import com.mobilemail.domain.usecase.ResolvePushNavigationUseCase
import com.mobilemail.domain.usecase.ResolveValidSessionUseCase
import com.mobilemail.notifications.PushNavigationStore
import com.mobilemail.ui.login.LoginScreen
import com.mobilemail.ui.login.LoginViewModel
import com.mobilemail.ui.messagedetail.MessageDetailScreen
import com.mobilemail.ui.messagedetail.MessageDetailViewModel
import com.mobilemail.ui.messagedetail.MessageDetailViewModelFactory
import com.mobilemail.ui.messages.MessagesScreen
import com.mobilemail.ui.messages.MessagesViewModel
import com.mobilemail.ui.messages.MessagesViewModelFactory
import com.mobilemail.ui.newmessage.ComposePrefillStore
import com.mobilemail.ui.newmessage.ComposeViewModel
import com.mobilemail.ui.newmessage.ComposeViewModelFactory
import com.mobilemail.ui.newmessage.NewMessageScreen
import com.mobilemail.ui.newmessage.ReplyAction
import com.mobilemail.ui.orchestration.MessageListBridgeCoordinator
import com.mobilemail.ui.outbox.OutboxScreen
import com.mobilemail.ui.outbox.OutboxViewModel
import com.mobilemail.ui.outbox.OutboxViewModelFactory
import com.mobilemail.ui.search.SearchScreen
import com.mobilemail.ui.search.SearchViewModel
import com.mobilemail.ui.search.SearchViewModelFactory
import com.mobilemail.ui.security.PinLockScreen
import com.mobilemail.ui.security.PinLockViewModel
import com.mobilemail.ui.security.PinLockViewModelFactory
import com.mobilemail.ui.security.PinSetupScreen
import com.mobilemail.ui.security.PinSetupViewModel
import com.mobilemail.ui.security.PinSetupViewModelFactory
import com.mobilemail.ui.settings.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    dependencies: AppNavigationDependencies,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as Application
    val pendingPushTarget by PushNavigationStore.pendingTarget.collectAsState()
    val preferencesManager = dependencies.preferencesManager
    val tokenStore = dependencies.tokenStore
    val activityScope = dependencies.activityScope
    val database = dependencies.database
    val resolveValidSessionUseCase = dependencies.resolveValidSessionUseCase
    val logoutAccountUseCase = dependencies.logoutAccountUseCase
    val logoutAllUseCase = dependencies.logoutAllUseCase
    val resolvePushNavigationUseCase = dependencies.resolvePushNavigationUseCase
    val handleMessagesStartupUseCase = dependencies.handleMessagesStartupUseCase
    val resolveMessagesViewModelContextUseCase = dependencies.resolveMessagesViewModelContextUseCase
    val accountPushTopicsPort = dependencies.accountPushTopicsPort

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoutes.PinLock) {
            val viewModel: PinLockViewModel = viewModel(
                factory = PinLockViewModelFactory(application)
            )
            PinLockScreen(
                viewModel = viewModel,
                onUnlocked = {
                    navController.navigate(AppRoutes.Login) {
                        popUpTo(AppRoutes.PinLock) { inclusive = true }
                    }
                },
                onLogout = {
                    activityScope.launch {
                        val accountIds = preferencesManager.getSavedAccounts().map { it.accountId }
                        logoutAllUseCase(
                            accountIds = accountIds,
                            unsubscribeTopic = accountPushTopicsPort::unsubscribe,
                            clearAllSessions = { preferencesManager.clearAllSessions() },
                            clearAllTokens = { tokenStore.clearAllTokens() },
                            clearJmapCaches = {
                                JmapOAuthClient.clearCache()
                            }
                        )
                        navController.navigate(AppRoutes.Login) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(AppRoutes.Login) {
            LaunchedEffect(Unit) {
                val activeSession = preferencesManager.getSavedSession()
                val savedAccounts = preferencesManager.getSavedAccounts()
                val validSession = resolveValidSessionUseCase(
                    activeSession = activeSession,
                    savedAccounts = savedAccounts
                ) { session ->
                    val tokens = tokenStore.getTokens(session.server, session.email)
                    tokens != null && (tokens.isValid() || tokens.refreshToken != null)
                }

                if (validSession != null) {
                    preferencesManager.setActiveSession(validSession)
                    navController.navigate(AppRoutes.messages(validSession)) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            val viewModel: LoginViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return LoginViewModel(application, autoLoginEnabled = true) as T
                    }
                }
            )
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = { server, email, _, accountId ->
                    navController.navigate(
                        AppRoutes.messages(SavedSession(server, email, accountId))
                    ) {
                        popUpTo(AppRoutes.Login) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoutes.AddAccount) {
            val viewModel: LoginViewModel = viewModel(
                key = "add_account_login",
                factory = object : ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return LoginViewModel(application, autoLoginEnabled = false) as T
                    }
                }
            )
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = { server, email, _, accountId ->
                    navController.navigate(AppRoutes.messages(SavedSession(server, email, accountId))) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoutes.MessagesPattern) { backStackEntry ->
            val routeArgs = backStackEntry.decodeAccountRouteArgs() ?: return@composable
            val server = routeArgs.server
            val email = routeArgs.email
            val accountId = routeArgs.accountId
            val currentSession = remember(server, email, accountId) { SavedSession(server, email, accountId) }
            val savedAccounts by preferencesManager.savedAccounts.collectAsState(initial = emptyList())

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                accountPushTopicsPort.subscribe(accountId)
            }

            LaunchedEffect(accountId) {
                when (
                    handleMessagesStartupUseCase(
                        accountId = accountId,
                        sdkInt = Build.VERSION.SDK_INT,
                        tiramisuSdkInt = Build.VERSION_CODES.TIRAMISU,
                        alreadyRequestedPermission = preferencesManager.isNotificationPermissionRequested(),
                        processPending = { OfflineQueueManager.processPending(application) },
                        subscribeToTopic = accountPushTopicsPort::subscribe,
                        markPermissionRequested = { preferencesManager.markNotificationPermissionRequested() }
                    )
                ) {
                    HandleMessagesStartupUseCase.Action.RequestNotificationPermission -> {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    HandleMessagesStartupUseCase.Action.NoPermissionRequest -> Unit
                }
            }

            val messagesViewModelContext = remember(server, email, accountId) {
                resolveMessagesViewModelContextUseCase(
                    server = server,
                    email = email,
                    accountId = accountId,
                    buildMessagesRoute = { AppRoutes.messages(it) }
                )
            }
            val viewModel: MessagesViewModel = viewModel(
                key = messagesViewModelContext.key,
                factory = MessagesViewModelFactory(server, email, accountId, database, application)
            )

            LaunchedEffect(pendingPushTarget, currentSession, savedAccounts) {
                val target = pendingPushTarget ?: return@LaunchedEffect
                when (
                    val action = resolvePushNavigationUseCase.resolveInMessages(
                        ResolvePushNavigationUseCase.MessagesInput(
                            target = target,
                            currentSession = currentSession,
                            savedAccounts = savedAccounts
                        )
                    )
                ) {
                    is ResolvePushNavigationUseCase.MessagesAction.SwitchSessionAndOpenInbox -> {
                        preferencesManager.setActiveSession(action.session)
                        navController.navigate(AppRoutes.messages(action.session)) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    is ResolvePushNavigationUseCase.MessagesAction.OpenMessage -> {
                        PushNavigationStore.clear(target)
                        navController.navigate(AppRoutes.message(currentSession, action.messageId)) {
                            launchSingleTop = true
                        }
                    }
                }
            }

            MessagesScreen(
                viewModel = viewModel,
                accounts = savedAccounts,
                activeAccountEmail = email,
                onMessageClick = { messageId ->
                    navController.navigate(
                        AppRoutes.message(
                            SavedSession(server, email, accountId),
                            messageId
                        )
                    )
                },
                detailPane = { selectedMessageId ->
                    if (selectedMessageId == null) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Выберите письмо для просмотра",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val detailViewModel: MessageDetailViewModel = viewModel(
                            key = "embedded_message_${server}_${email}_${accountId}_$selectedMessageId",
                            factory = MessageDetailViewModelFactory(application, server, email, accountId, selectedMessageId)
                        )
                        val bridgeCoordinator = remember(viewModel) {
                            MessageListBridgeCoordinator(
                                removeMessage = { viewModel.removeMessage(it) },
                                updateReadStatus = { id, unread ->
                                    viewModel.updateMessageReadStatus(id, unread)
                                }
                            )
                        }
                        LaunchedEffect(detailViewModel, viewModel) {
                            bridgeCoordinator.bindDetailReadStatusCallback {
                                detailViewModel.setOnReadStatusChanged(it)
                            }
                        }
                        MessageDetailScreen(
                            viewModel = detailViewModel,
                            onBack = { viewModel.selectMessage(null) },
                            onReply = { message ->
                                navController.navigate(replyDraftRoute(server, email, accountId, message, ReplyAction.REPLY))
                            },
                            onReplyAll = { message ->
                                navController.navigate(replyDraftRoute(server, email, accountId, message, ReplyAction.REPLY_ALL))
                            },
                            onForward = { message ->
                                navController.navigate(replyDraftRoute(server, email, accountId, message, ReplyAction.FORWARD))
                            },
                            onMessageDeleted = bridgeCoordinator::onMessageDeleted,
                            onMessageMoved = bridgeCoordinator::onMessageMoved,
                            onReadStatusChanged = bridgeCoordinator::onReadStatusChanged,
                            onThreadMessageClick = { threadMessageId ->
                                viewModel.selectMessage(threadMessageId)
                            }
                        )
                    }
                },
                onSearchClick = {
                    navController.navigate(AppRoutes.search(server, email, accountId))
                },
                onComposeClick = {
                    navController.navigate(AppRoutes.compose(server, email, accountId))
                },
                onAddAccountClick = {
                    navController.navigate(AppRoutes.AddAccount)
                },
                onSwitchAccount = { session ->
                    if (session.server != server || session.email != email || session.accountId != accountId) {
                        activityScope.launch {
                            preferencesManager.setActiveSession(session)
                            navController.navigate(AppRoutes.messages(session)) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                },
                onOutboxClick = {
                    navController.navigate(AppRoutes.outbox(server, email, accountId))
                },
                onSettingsClick = {
                    navController.navigate(AppRoutes.settings(server, email))
                },
                onLogout = {
                    activityScope.launch {
                        val nextSession = logoutAccountUseCase(
                            session = SavedSession(server, email, accountId),
                            unsubscribeTopic = accountPushTopicsPort::unsubscribe,
                            clearTokens = { targetServer, targetEmail ->
                                tokenStore.clearTokens(targetServer, targetEmail)
                            },
                            removeSavedAccount = { targetServer, targetEmail ->
                                preferencesManager.removeSavedAccount(targetServer, targetEmail)
                            }
                        )
                        JmapOAuthClient.clearCache()
                        val target = nextSession?.let { AppRoutes.messages(it) } ?: AppRoutes.Login
                        navController.navigate(target) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(AppRoutes.ComposePattern) { backStackEntry ->
            val routeArgs = backStackEntry.decodeComposeRouteArgs() ?: return@composable
            val server = routeArgs.server
            val email = routeArgs.email
            val accountId = routeArgs.accountId
            val draftToken = routeArgs.draftToken
            val prefill = remember(draftToken) { ComposePrefillStore.consume(draftToken) }

            val viewModel: ComposeViewModel = viewModel(
                factory = ComposeViewModelFactory(application, server, email, accountId)
            )
            NewMessageScreen(
                viewModel = viewModel,
                server = server,
                email = email,
                accountId = accountId,
                initialTo = prefill?.to.orEmpty(),
                initialSubject = prefill?.subject.orEmpty(),
                initialBody = prefill?.body.orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.SearchPattern) { backStackEntry ->
            val routeArgs = backStackEntry.decodeAccountRouteArgs() ?: return@composable
            val server = routeArgs.server
            val email = routeArgs.email
            val accountId = routeArgs.accountId

            val viewModel: SearchViewModel = viewModel(
                factory = SearchViewModelFactory(application, server, email, accountId)
            )
            SearchScreen(
                viewModel = viewModel,
                onMessageClick = { messageId ->
                    navController.navigate(
                        AppRoutes.message(
                            SavedSession(server, email, accountId),
                            messageId
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.OutboxPattern) { backStackEntry ->
            val routeArgs = backStackEntry.decodeAccountRouteArgs() ?: return@composable
            val server = routeArgs.server
            val email = routeArgs.email
            val accountId = routeArgs.accountId
            val viewModel: OutboxViewModel = viewModel(
                factory = OutboxViewModelFactory(application, server, email, accountId)
            )
            OutboxScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.SettingsPattern) { backStackEntry ->
            val routeArgs = backStackEntry.decodeSettingsRouteArgs() ?: return@composable
            val server = routeArgs.server
            val email = routeArgs.email

            SettingsScreen(
                server = server,
                email = email,
                preferencesManager = preferencesManager,
                onBack = { navController.popBackStack() },
                onPinSetupClick = {
                    navController.navigate(AppRoutes.PinSetup)
                }
            )
        }

        composable(AppRoutes.PinSetup) {
            val viewModel: PinSetupViewModel = viewModel(
                factory = PinSetupViewModelFactory(application)
            )
            PinSetupScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.MessagePattern) { backStackEntry ->
            val routeArgs = backStackEntry.decodeMessageRouteArgs() ?: return@composable
            val server = routeArgs.server
            val email = routeArgs.email
            val accountId = routeArgs.accountId
            val messageId = routeArgs.messageId
            val viewModel: MessageDetailViewModel = viewModel(
                factory = MessageDetailViewModelFactory(application, server, email, accountId, messageId)
            )

            val messagesViewModelContext = remember(server, email, accountId) {
                resolveMessagesViewModelContextUseCase(
                    server = server,
                    email = email,
                    accountId = accountId,
                    buildMessagesRoute = { AppRoutes.messages(it) }
                )
            }
            val parentEntry = remember(backStackEntry, messagesViewModelContext.route) {
                navController.getBackStackEntry(messagesViewModelContext.route)
            }

            val messagesViewModel: MessagesViewModel = viewModel(
                parentEntry,
                key = messagesViewModelContext.key,
                factory = MessagesViewModelFactory(server, email, accountId, database, application)
            )
            val bridgeCoordinator = remember(messagesViewModel) {
                MessageListBridgeCoordinator(
                    removeMessage = { messagesViewModel.removeMessage(it) },
                    updateReadStatus = { id, unread ->
                        messagesViewModel.updateMessageReadStatus(id, unread)
                    }
                )
            }

            LaunchedEffect(viewModel, messagesViewModel) {
                bridgeCoordinator.bindDetailReadStatusCallback {
                    viewModel.setOnReadStatusChanged(it)
                }
            }

            MessageDetailScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
                onReply = { message ->
                    navController.navigate(replyDraftRoute(server, email, accountId, message, ReplyAction.REPLY))
                },
                onReplyAll = { message ->
                    navController.navigate(replyDraftRoute(server, email, accountId, message, ReplyAction.REPLY_ALL))
                },
                onForward = { message ->
                    navController.navigate(replyDraftRoute(server, email, accountId, message, ReplyAction.FORWARD))
                },
                onMessageDeleted = bridgeCoordinator::onMessageDeleted,
                onMessageMoved = bridgeCoordinator::onMessageMoved,
                onReadStatusChanged = bridgeCoordinator::onReadStatusChanged,
                onThreadMessageClick = { threadMessageId ->
                    if (threadMessageId != messageId) {
                        navController.navigate(
                            AppRoutes.message(
                                SavedSession(server, email, accountId),
                                threadMessageId
                            ),
                            navOptions {
                                launchSingleTop = true
                            }
                        )
                    }
                }
            )
        }
    }
}

private fun replyDraftRoute(
    server: String,
    email: String,
    accountId: String,
    message: MessageDetail,
    action: ReplyAction,
): String {
    val token = ComposePrefillStore.createReplyDraft(message, email, action)
    return AppRoutes.compose(server, email, accountId, token)
}
