package com.mobilemail

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.room.Room
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.jmap.JmapOAuthClient
import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.ui.theme.MobileMailTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mobilemail.ui.login.LoginScreen
import com.mobilemail.ui.login.LoginViewModel
import com.mobilemail.ui.messagedetail.MessageDetailScreen
import com.mobilemail.ui.messagedetail.MessageDetailViewModel
import com.mobilemail.ui.messagedetail.MessageDetailViewModelFactory
import com.mobilemail.ui.messages.MessagesScreen
import com.mobilemail.ui.messages.MessagesViewModel
import com.mobilemail.ui.messages.MessagesViewModelFactory
import com.mobilemail.ui.search.SearchScreen
import com.mobilemail.ui.search.SearchViewModel
import com.mobilemail.ui.search.SearchViewModelFactory

class MainActivity : ComponentActivity() {
    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }
    private val preferencesManager by lazy { PreferencesManager(applicationContext) }
    private val tokenStore by lazy { TokenStore(applicationContext) }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileMailTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    LaunchedEffect(Unit) {
                        val savedSession = preferencesManager.getSavedSession()
                        if (savedSession != null) {
                            val tokens = tokenStore.getTokens(savedSession.server, savedSession.email)
                            if (tokens != null) {
                                val hasRefreshToken = tokens.refreshToken != null
                                val isAccessTokenValid = tokens.isValid()
                                android.util.Log.d("MainActivity", "Найдена сохраненная OAuth сессия: access_valid=$isAccessTokenValid, has_refresh=$hasRefreshToken")
                                
                                if (isAccessTokenValid || hasRefreshToken) {
                                    android.util.Log.d("MainActivity", "Автоматический вход через OAuth")
                                    val route = "messages/${Uri.encode(savedSession.server)}/${Uri.encode(savedSession.email)}//${Uri.encode(savedSession.accountId)}"
                                    navController.navigate(route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                } else {
                                    android.util.Log.w("MainActivity", "OAuth токены истекли и нет refresh token, требуется повторная авторизация")
                                    preferencesManager.clearSession()
                                    tokenStore.clearTokens(savedSession.server, savedSession.email)
                                }
                            } else {
                                android.util.Log.d("MainActivity", "OAuth токены не найдены")
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "login"
                    ) {
                        composable("login") {
                            val viewModel: LoginViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return LoginViewModel(application) as T
                                    }
                                }
                            )
                            LoginScreen(
                                viewModel = viewModel,
                                onLoginSuccess = { server, email, _, accountId ->
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedAccountId = Uri.encode(accountId)
                                    navController.navigate("messages/$encodedServer/$encodedEmail//$encodedAccountId") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("messages/{server}/{email}/{password}/{accountId}") { backStackEntry ->
                            val server = Uri.decode(backStackEntry.arguments?.getString("server") ?: return@composable)
                            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: return@composable)
                            val password = Uri.decode(backStackEntry.arguments?.getString("password") ?: return@composable)
                            val accountId = Uri.decode(backStackEntry.arguments?.getString("accountId") ?: return@composable)

                            android.util.Log.d("MainActivity", "Создание MessagesScreen: server=$server, email=$email, accountId=$accountId")
                            
                            val viewModel: MessagesViewModel = viewModel(
                                key = "messages_${server}_${email}_${accountId}",
                                factory = MessagesViewModelFactory(server, email, password, accountId, database, application)
                            )
                            
                            android.util.Log.d("MainActivity", "MessagesViewModel создан, состояние: isLoading=${viewModel.uiState.value.isLoading}")
                            MessagesScreen(
                                viewModel = viewModel,
                                onMessageClick = { messageId ->
                                    android.util.Log.d("MainActivity", "Открытие письма: messageId=$messageId")
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedAccountId = Uri.encode(accountId)
                                    val encodedMessageId = Uri.encode(messageId)
                                    android.util.Log.d("MainActivity", "Навигация с encodedMessageId=$encodedMessageId")
                                    navController.navigate("message/$encodedServer/$encodedEmail//$encodedAccountId/$encodedMessageId")
                                },
                                onSearchClick = {
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedAccountId = Uri.encode(accountId)
                                    navController.navigate("search/$encodedServer/$encodedEmail//$encodedAccountId")
                                },
                                onLogout = {
                                    activityScope.launch {
                                        android.util.Log.d("MainActivity", "Выход из приложения: server=$server, email=$email")
                                        preferencesManager.clearSession()
                                        tokenStore.clearTokens(server, email)
                                        JmapOAuthClient.clearCache()
                                        JmapClient.clearCache()
                                        android.util.Log.d("MainActivity", "Сессия и токены очищены, выход выполнен")
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable("search/{server}/{email}/{password}/{accountId}") { backStackEntry ->
                            val server = Uri.decode(backStackEntry.arguments?.getString("server") ?: return@composable)
                            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: return@composable)
                            val password = Uri.decode(backStackEntry.arguments?.getString("password") ?: return@composable)
                            val accountId = Uri.decode(backStackEntry.arguments?.getString("accountId") ?: return@composable)

                            val viewModel: SearchViewModel = viewModel(
                                factory = SearchViewModelFactory(server, email, password, accountId)
                            )
                            SearchScreen(
                                viewModel = viewModel,
                                onMessageClick = { messageId ->
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedPassword = Uri.encode(password)
                                    val encodedAccountId = Uri.encode(accountId)
                                    val encodedMessageId = Uri.encode(messageId)
                                    navController.navigate("message/$encodedServer/$encodedEmail/$encodedPassword/$encodedAccountId/$encodedMessageId")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("message/{server}/{email}/{password}/{accountId}/{messageId}") { backStackEntry ->
                            val server = Uri.decode(backStackEntry.arguments?.getString("server") ?: return@composable)
                            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: return@composable)
                            val password = Uri.decode(backStackEntry.arguments?.getString("password") ?: return@composable)
                            val accountId = Uri.decode(backStackEntry.arguments?.getString("accountId") ?: return@composable)
                            val messageId = Uri.decode(backStackEntry.arguments?.getString("messageId") ?: return@composable)
                            android.util.Log.d("MainActivity", "Создание MessageDetailScreen: messageId=$messageId, accountId=$accountId")

                            val viewModel: MessageDetailViewModel = viewModel(
                                factory = MessageDetailViewModelFactory(application, server, email, password, accountId, messageId)
                            )
                            
                            val messagesRoute = "messages/${Uri.encode(server)}/${Uri.encode(email)}/${Uri.encode(password)}/${Uri.encode(accountId)}"
                            val parentEntry = remember(messagesRoute) { navController.getBackStackEntry(messagesRoute) }
                            
                            val messagesViewModel: MessagesViewModel = viewModel(
                                parentEntry,
                                key = "messages_${server}_${email}_${accountId}",
                                factory = MessagesViewModelFactory(server, email, password, accountId, database, application)
                            )
                            
                            LaunchedEffect(viewModel, messagesViewModel) {
                                viewModel.setOnReadStatusChanged { messageId, isUnread ->
                                    android.util.Log.d("MainActivity", "Callback onReadStatusChanged вызван: messageId=$messageId, isUnread=$isUnread")
                                    messagesViewModel.updateMessageReadStatus(messageId, isUnread)
                                }
                            }
                            
                            MessageDetailScreen(
                                viewModel = viewModel,
                                onBack = { 
                                    navController.popBackStack()
                                },
                                onMessageDeleted = { deletedMessageId ->
                                    messagesViewModel.removeMessage(deletedMessageId)
                                },
                                onReadStatusChanged = { messageId, isUnread ->
                                    android.util.Log.d("MainActivity", "onReadStatusChanged вызван: messageId=$messageId, isUnread=$isUnread")
                                    messagesViewModel.updateMessageReadStatus(messageId, isUnread)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
