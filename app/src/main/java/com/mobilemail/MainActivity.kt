package com.mobilemail

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.jmap.JmapOAuthClient
import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.ui.theme.MobileMailTheme
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.navOptions
import com.mobilemail.notifications.PushMessageTarget
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
import com.mobilemail.ui.outbox.OutboxScreen
import com.mobilemail.ui.outbox.OutboxViewModel
import com.mobilemail.ui.outbox.OutboxViewModelFactory
import com.mobilemail.ui.search.SearchScreen
import com.mobilemail.ui.search.SearchViewModel
import com.mobilemail.ui.search.SearchViewModelFactory
import com.mobilemail.ui.settings.SettingsScreen
import com.mobilemail.ui.security.PinSetupScreen
import com.mobilemail.ui.security.PinSetupViewModel
import com.mobilemail.ui.security.PinSetupViewModelFactory
import com.mobilemail.ui.security.PinLockScreen
import com.mobilemail.ui.security.PinLockViewModel
import com.mobilemail.ui.security.PinLockViewModelFactory
import com.mobilemail.data.security.PinManager
import com.mobilemail.data.sync.OfflineQueueManager

class MainActivity : FragmentActivity() {
    private val database by lazy {
        AppDatabase.getInstance(applicationContext)
    }
    private val preferencesManager by lazy { PreferencesManager(applicationContext) }
    private val tokenStore by lazy { TokenStore(applicationContext) }
    private val pinManager by lazy { PinManager(applicationContext) }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun buildComposeRoute(
        server: String,
        email: String,
        accountId: String,
        draftToken: String = "-"
    ): String {
        return "compose/${Uri.encode(server)}/${Uri.encode(email)}/${Uri.encode(accountId)}/${Uri.encode(draftToken)}"
    }

    private fun buildReplyDraftRoute(
        server: String,
        email: String,
        accountId: String,
        message: com.mobilemail.data.model.MessageDetail,
        action: ReplyAction
    ): String {
        val token = ComposePrefillStore.createReplyDraft(message, email, action)
        return buildComposeRoute(server, email, accountId, token)
    }

    private fun buildMessagesRoute(session: SavedSession): String {
        return "messages/${Uri.encode(session.server)}/${Uri.encode(session.email)}/${Uri.encode(session.accountId)}"
    }

    private fun buildMessageRoute(session: SavedSession, messageId: String): String {
        return "message/${Uri.encode(session.server)}/${Uri.encode(session.email)}/${Uri.encode(session.accountId)}/${Uri.encode(messageId)}"
    }

    private fun matchesPushSession(target: PushMessageTarget, session: SavedSession): Boolean {
        return (target.server == null || target.server == session.server) &&
            (target.email == null || target.email == session.email) &&
            (target.accountId == null || target.accountId == session.accountId)
    }

    private fun resolvePushSession(
        target: PushMessageTarget,
        currentSession: SavedSession?,
        savedAccounts: List<SavedSession>
    ): SavedSession? {
        currentSession?.takeIf { matchesPushSession(target, it) }?.let { return it }
        savedAccounts.firstOrNull { matchesPushSession(target, it) }?.let { return it }
        return currentSession ?: savedAccounts.lastOrNull()
    }

    private suspend fun logoutAccount(session: SavedSession): SavedSession? {
        tokenStore.clearTokens(session.server, session.email)
        return preferencesManager.removeSavedAccount(session.server, session.email)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileMailTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val privacyScreenProtection by preferencesManager.privacyScreenProtection.collectAsState(initial = true)
                    val pendingPushTarget by PushNavigationStore.pendingTarget.collectAsState()
                    val startDestination = if (pinManager.isPinEnabled()) "pin-lock" else "login"

                    LaunchedEffect(privacyScreenProtection) {
                        if (privacyScreenProtection) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }

                    LaunchedEffect(pendingPushTarget, currentBackStackEntry?.destination?.route) {
                        val target = pendingPushTarget ?: return@LaunchedEffect
                        val currentRoute = currentBackStackEntry?.destination?.route
                        if (currentRoute == null || currentRoute == "pin-lock" || currentRoute == "messages/{server}/{email}/{accountId}") {
                            return@LaunchedEffect
                        }

                        val activeSession = preferencesManager.getSavedSession()
                        val savedAccounts = preferencesManager.getSavedAccounts()
                        val resolvedSession = resolvePushSession(target, activeSession, savedAccounts) ?: return@LaunchedEffect

                        if (activeSession != resolvedSession) {
                            preferencesManager.setActiveSession(resolvedSession)
                        }

                        navController.navigate(buildMessagesRoute(resolvedSession)) {
                            launchSingleTop = true
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("pin-lock") {
                            val viewModel: PinLockViewModel = viewModel(
                                factory = PinLockViewModelFactory(application)
                            )
                            PinLockScreen(
                                viewModel = viewModel,
                                onUnlocked = {
                                    navController.navigate("login") {
                                        popUpTo("pin-lock") { inclusive = true }
                                    }
                                },
                                onLogout = {
                                    activityScope.launch {
                                        preferencesManager.clearAllSessions()
                                        tokenStore.clearAllTokens()
                                        JmapOAuthClient.clearCache()
                                        JmapClient.clearCache()
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable("login") {
                            // Автовход через OAuth (только после прохождения PIN, если он включён)
                            LaunchedEffect(Unit) {
                                val activeSession = preferencesManager.getSavedSession()
                                val savedAccounts = preferencesManager.getSavedAccounts()
                                val orderedCandidates = buildList {
                                    if (activeSession != null) add(activeSession)
                                    addAll(savedAccounts.filterNot {
                                        it.server == activeSession?.server && it.email == activeSession?.email
                                    })
                                }
                                val validSession = orderedCandidates.firstOrNull { session ->
                                    val tokens = tokenStore.getTokens(session.server, session.email)
                                    tokens != null && (tokens.isValid() || tokens.refreshToken != null)
                                }

                                if (validSession != null) {
                                    preferencesManager.setActiveSession(validSession)
                                    navController.navigate(buildMessagesRoute(validSession)) {
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
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedAccountId = Uri.encode(accountId)
                                    navController.navigate("messages/$encodedServer/$encodedEmail/$encodedAccountId") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("add-account") {
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
                                    navController.navigate(buildMessagesRoute(SavedSession(server, email, accountId))) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("messages/{server}/{email}/{accountId}") { backStackEntry ->
                            val server = Uri.decode(backStackEntry.arguments?.getString("server") ?: return@composable)
                            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: return@composable)
                            val accountId = Uri.decode(backStackEntry.arguments?.getString("accountId") ?: return@composable)
                            val currentSession = remember(server, email, accountId) { SavedSession(server, email, accountId) }
                            val savedAccounts by preferencesManager.savedAccounts.collectAsState(initial = emptyList())

                            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestPermission()
                            ) { granted ->
                                if (granted) {
                                    OneSignal.User.pushSubscription.optIn()
                                }
                                OneSignal.login(email)
                            }

                            LaunchedEffect(email) {
                                OfflineQueueManager.processPending(application)
                                val alreadyRequested = preferencesManager.isNotificationPermissionRequested()
                                if (!alreadyRequested) {
                                    preferencesManager.markNotificationPermissionRequested()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        OneSignal.User.pushSubscription.optIn()
                                        OneSignal.login(email)
                                    }
                                } else {
                                    // Always ensure push subscription is active on app relaunch
                                    OneSignal.User.pushSubscription.optIn()
                                    OneSignal.login(email)
                                }
                            }

                            val viewModel: MessagesViewModel = viewModel(
                                key = "messages_${server}_${email}_${accountId}",
                                factory = MessagesViewModelFactory(server, email, accountId, database, application)
                            )

                            LaunchedEffect(pendingPushTarget, currentSession, savedAccounts) {
                                val target = pendingPushTarget ?: return@LaunchedEffect
                                val resolvedSession = resolvePushSession(target, currentSession, savedAccounts) ?: return@LaunchedEffect

                                if (resolvedSession != currentSession) {
                                    preferencesManager.setActiveSession(resolvedSession)
                                    navController.navigate(buildMessagesRoute(resolvedSession)) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                    return@LaunchedEffect
                                }

                                PushNavigationStore.clear(target)
                                navController.navigate(buildMessageRoute(currentSession, target.messageId)) {
                                    launchSingleTop = true
                                }
                            }

                            MessagesScreen(
                                viewModel = viewModel,
                                accounts = savedAccounts,
                                activeAccountEmail = email,
                                onMessageClick = { messageId ->
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedAccountId = Uri.encode(accountId)
                                    val encodedMessageId = Uri.encode(messageId)
                                    navController.navigate("message/$encodedServer/$encodedEmail/$encodedAccountId/$encodedMessageId")
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
                                        LaunchedEffect(detailViewModel, viewModel) {
                                            detailViewModel.setOnReadStatusChanged { updatedMessageId, isUnread ->
                                                viewModel.updateMessageReadStatus(updatedMessageId, isUnread)
                                            }
                                        }
                                        MessageDetailScreen(
                                            viewModel = detailViewModel,
                                            onBack = { viewModel.selectMessage(null) },
                                            onReply = { message ->
                                                navController.navigate(buildReplyDraftRoute(server, email, accountId, message, ReplyAction.REPLY))
                                            },
                                            onReplyAll = { message ->
                                                navController.navigate(buildReplyDraftRoute(server, email, accountId, message, ReplyAction.REPLY_ALL))
                                            },
                                            onForward = { message ->
                                                navController.navigate(buildReplyDraftRoute(server, email, accountId, message, ReplyAction.FORWARD))
                                            },
                                            onMessageDeleted = { deletedMessageId ->
                                                viewModel.removeMessage(deletedMessageId)
                                            },
                                            onMessageMoved = { movedMessageId ->
                                                viewModel.removeMessage(movedMessageId)
                                            },
                                            onReadStatusChanged = { updatedMessageId, isUnread ->
                                                viewModel.updateMessageReadStatus(updatedMessageId, isUnread)
                                            },
                                            onThreadMessageClick = { threadMessageId ->
                                                viewModel.selectMessage(threadMessageId)
                                            }
                                        )
                                    }
                                },
                                onSearchClick = {
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedAccountId = Uri.encode(accountId)
                                    navController.navigate("search/$encodedServer/$encodedEmail/$encodedAccountId")
                                },
                                onComposeClick = {
                                    navController.navigate(buildComposeRoute(server, email, accountId))
                                },
                                onAddAccountClick = {
                                    navController.navigate("add-account")
                                },
                                onSwitchAccount = { session ->
                                    if (session.server != server || session.email != email || session.accountId != accountId) {
                                        activityScope.launch {
                                            preferencesManager.setActiveSession(session)
                                            navController.navigate(buildMessagesRoute(session)) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    }
                                },
                                onOutboxClick = {
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedAccountId = Uri.encode(accountId)
                                    navController.navigate("outbox/$encodedServer/$encodedEmail/$encodedAccountId")
                                },
                                onSettingsClick = {
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    navController.navigate("settings/$encodedServer/$encodedEmail")
                                },
                                onLogout = {
                                    activityScope.launch {
                                        val nextSession = logoutAccount(SavedSession(server, email, accountId))
                                        JmapOAuthClient.clearCache()
                                        JmapClient.clearCache()
                                        val target = nextSession?.let { buildMessagesRoute(it) } ?: "login"
                                        navController.navigate(target) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable("compose/{server}/{email}/{accountId}/{draftToken}") { backStackEntry ->
                            val server = Uri.decode(backStackEntry.arguments?.getString("server") ?: return@composable)
                            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: return@composable)
                            val accountId = Uri.decode(backStackEntry.arguments?.getString("accountId") ?: return@composable)
                            val draftToken = Uri.decode(backStackEntry.arguments?.getString("draftToken") ?: "-")
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

                        composable("search/{server}/{email}/{accountId}") { backStackEntry ->
                            val server = Uri.decode(backStackEntry.arguments?.getString("server") ?: return@composable)
                            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: return@composable)
                            val accountId = Uri.decode(backStackEntry.arguments?.getString("accountId") ?: return@composable)

                            val viewModel: SearchViewModel = viewModel(
                                factory = SearchViewModelFactory(application, server, email, accountId)
                            )
                            SearchScreen(
                                viewModel = viewModel,
                                onMessageClick = { messageId ->
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedAccountId = Uri.encode(accountId)
                                    val encodedMessageId = Uri.encode(messageId)
                                    navController.navigate("message/$encodedServer/$encodedEmail/$encodedAccountId/$encodedMessageId")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("outbox/{server}/{email}/{accountId}") { backStackEntry ->
                            val server = Uri.decode(backStackEntry.arguments?.getString("server") ?: return@composable)
                            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: return@composable)
                            val accountId = Uri.decode(backStackEntry.arguments?.getString("accountId") ?: return@composable)
                            val viewModel: OutboxViewModel = viewModel(
                                factory = OutboxViewModelFactory(application, server, email, accountId)
                            )
                            OutboxScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings/{server}/{email}") { backStackEntry ->
                            val server = Uri.decode(backStackEntry.arguments?.getString("server") ?: return@composable)
                            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: return@composable)

                            SettingsScreen(
                                server = server,
                                email = email,
                                preferencesManager = preferencesManager,
                                onBack = { navController.popBackStack() },
                                onPinSetupClick = {
                                    navController.navigate("pin-setup")
                                }
                            )
                        }

                        composable("pin-setup") {
                            val viewModel: PinSetupViewModel = viewModel(
                                factory = PinSetupViewModelFactory(application)
                            )
                            PinSetupScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("message/{server}/{email}/{accountId}/{messageId}") { backStackEntry ->
                            val server = Uri.decode(backStackEntry.arguments?.getString("server") ?: return@composable)
                            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: return@composable)
                            val accountId = Uri.decode(backStackEntry.arguments?.getString("accountId") ?: return@composable)
                            val messageId = Uri.decode(backStackEntry.arguments?.getString("messageId") ?: return@composable)
                            val viewModel: MessageDetailViewModel = viewModel(
                                factory = MessageDetailViewModelFactory(application, server, email, accountId, messageId)
                            )

                            val messagesRoute = "messages/${Uri.encode(server)}/${Uri.encode(email)}/${Uri.encode(accountId)}"
                            val parentEntry = remember(messagesRoute) { navController.getBackStackEntry(messagesRoute) }

                            val messagesViewModel: MessagesViewModel = viewModel(
                                parentEntry,
                                key = "messages_${server}_${email}_${accountId}",
                                factory = MessagesViewModelFactory(server, email, accountId, database, application)
                            )
                            
                            LaunchedEffect(viewModel, messagesViewModel) {
                                viewModel.setOnReadStatusChanged { updatedMessageId, isUnread ->
                                    messagesViewModel.updateMessageReadStatus(updatedMessageId, isUnread)
                                }
                            }
                            
                            MessageDetailScreen(
                                viewModel = viewModel,
                                onBack = { 
                                    navController.popBackStack()
                                },
                                onReply = { message ->
                                    navController.navigate(buildReplyDraftRoute(server, email, accountId, message, ReplyAction.REPLY))
                                },
                                onReplyAll = { message ->
                                    navController.navigate(buildReplyDraftRoute(server, email, accountId, message, ReplyAction.REPLY_ALL))
                                },
                                onForward = { message ->
                                    navController.navigate(buildReplyDraftRoute(server, email, accountId, message, ReplyAction.FORWARD))
                                },
                                onMessageDeleted = { deletedMessageId ->
                                    messagesViewModel.removeMessage(deletedMessageId)
                                },
                                onMessageMoved = { movedMessageId ->
                                    messagesViewModel.removeMessage(movedMessageId)
                                },
                                onReadStatusChanged = { updatedMessageId, isUnread ->
                                    messagesViewModel.updateMessageReadStatus(updatedMessageId, isUnread)
                                },
                                onThreadMessageClick = { threadMessageId ->
                                    if (threadMessageId != messageId) {
                                        val encodedServer = Uri.encode(server)
                                        val encodedEmail = Uri.encode(email)
                                        val encodedAccountId = Uri.encode(accountId)
                                        val encodedMessageId = Uri.encode(threadMessageId)
                                        navController.navigate(
                                            "message/$encodedServer/$encodedEmail/$encodedAccountId/$encodedMessageId",
                                            navOptions {
                                                launchSingleTop = true
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // Безопасная обработка внешних deep-link'ов: проверяем через NavDeepLinkRequest
                    // и игнорируем те, что не совпадают с графом, чтобы не получить IllegalArgumentException.
                    val data = intent?.data
                    if (data != null) {
                        val request = NavDeepLinkRequest.Builder.fromUri(data).build()
                        if (navController.graph.hasDeepLink(request)) {
                            try {
                                navController.handleDeepLink(intent)
                            } catch (e: IllegalArgumentException) {
                                android.util.Log.w("MainActivity", "Deep link not matched, ignoring: $data", e)
                            }
                        } else {
                            android.util.Log.w("MainActivity", "Deep link not in graph, ignoring: $data")
                        }
                    }
                }
            }
        }
    }
}
