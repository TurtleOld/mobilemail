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
import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.data.security.CredentialManager
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
    private val credentialManager by lazy { CredentialManager(applicationContext) }
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

                    // Проверяем сохранённую сессию при запуске и навигируем
                    LaunchedEffect(Unit) {
                        val savedSession = preferencesManager.getSavedSession()
                        if (savedSession != null) {
                            val password = credentialManager.getPassword(savedSession.server, savedSession.email)
                            if (!password.isNullOrBlank()) {
                                android.util.Log.d("MainActivity", "Найдена сохраненная сессия, автоматический вход")
                                val route = "messages/${Uri.encode(savedSession.server)}/${Uri.encode(savedSession.email)}/${Uri.encode(password)}/${Uri.encode(savedSession.accountId)}"
                                navController.navigate(route) {
                                    popUpTo(0) { inclusive = true }
                                }
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
                                onLoginSuccess = { server, email, password, accountId ->
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedPassword = Uri.encode(password)
                                    val encodedAccountId = Uri.encode(accountId)
                                    navController.navigate("messages/$encodedServer/$encodedEmail/$encodedPassword/$encodedAccountId") {
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
                                factory = MessagesViewModelFactory(server, email, password, accountId, database)
                            )
                            
                            android.util.Log.d("MainActivity", "MessagesViewModel создан, состояние: isLoading=${viewModel.uiState.value.isLoading}")
                            MessagesScreen(
                                viewModel = viewModel,
                                onMessageClick = { messageId ->
                                    android.util.Log.d("MainActivity", "Открытие письма: messageId=$messageId")
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedPassword = Uri.encode(password)
                                    val encodedAccountId = Uri.encode(accountId)
                                    val encodedMessageId = Uri.encode(messageId)
                                    android.util.Log.d("MainActivity", "Навигация с encodedMessageId=$encodedMessageId")
                                    navController.navigate("message/$encodedServer/$encodedEmail/$encodedPassword/$encodedAccountId/$encodedMessageId")
                                },
                                onSearchClick = {
                                    val encodedServer = Uri.encode(server)
                                    val encodedEmail = Uri.encode(email)
                                    val encodedPassword = Uri.encode(password)
                                    val encodedAccountId = Uri.encode(accountId)
                                    navController.navigate("search/$encodedServer/$encodedEmail/$encodedPassword/$encodedAccountId")
                                },
                                onLogout = {
                                    activityScope.launch {
                                        preferencesManager.clearSession()
                                        credentialManager.clearCredentials(email)
                                        android.util.Log.d("MainActivity", "Сессия очищена, выход выполнен")
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
                                factory = MessagesViewModelFactory(server, email, password, accountId, database)
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
