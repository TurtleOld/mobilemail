package com.mobilemail

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mobilemail.ui.theme.MobileMailTheme
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileMailTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

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

                            val viewModel: MessagesViewModel = viewModel(
                                factory = MessagesViewModelFactory(server, email, password, accountId)
                            )
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
                                }
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
                                factory = MessageDetailViewModelFactory(server, email, password, accountId, messageId)
                            )
                            MessageDetailScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
