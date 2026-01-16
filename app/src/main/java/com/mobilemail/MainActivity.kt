package com.mobilemail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mobilemail.ui.theme.MobileMailTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mobilemail.ui.login.LoginScreen
import com.mobilemail.ui.login.LoginViewModel
import com.mobilemail.ui.messagedetail.MessageDetailScreen
import com.mobilemail.ui.messagedetail.MessageDetailViewModel
import com.mobilemail.ui.messages.MessagesScreen
import com.mobilemail.ui.messages.MessagesViewModel

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
                        startDestination = 'login'
                    ) {
                        composable('login') {
                            val viewModel: LoginViewModel = viewModel()
                            LoginScreen(
                                viewModel = viewModel,
                                onLoginSuccess = { server, email, password, accountId ->
                                    navController.navigate('messages/$server/$email/$password/$accountId') {
                                        popUpTo('login') { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable('messages/{server}/{email}/{password}/{accountId}') { backStackEntry ->
                            val server = backStackEntry.arguments?.getString('server') ?: return@composable
                            val email = backStackEntry.arguments?.getString('email') ?: return@composable
                            val password = backStackEntry.arguments?.getString('password') ?: return@composable
                            val accountId = backStackEntry.arguments?.getString('accountId') ?: return@composable

                            val viewModel: MessagesViewModel = viewModel(
                                factory = MessagesViewModelFactory(server, email, password, accountId)
                            )
                            MessagesScreen(
                                viewModel = viewModel,
                                onMessageClick = { messageId ->
                                    navController.navigate('message/$server/$email/$password/$accountId/$messageId')
                                }
                            )
                        }

                        composable('message/{server}/{email}/{password}/{accountId}/{messageId}') { backStackEntry ->
                            val server = backStackEntry.arguments?.getString('server') ?: return@composable
                            val email = backStackEntry.arguments?.getString('email') ?: return@composable
                            val password = backStackEntry.arguments?.getString('password') ?: return@composable
                            val accountId = backStackEntry.arguments?.getString('accountId') ?: return@composable
                            val messageId = backStackEntry.arguments?.getString('messageId') ?: return@composable

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
