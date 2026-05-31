package com.mobilemail.ui.navigation

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.domain.usecase.ResolvePushNavigationUseCase
import com.mobilemail.notifications.PushMessageTarget
import com.mobilemail.notifications.PushNavigationStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushColdStartNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val session = SavedSession(
        server = "mail.example.com",
        email = "user@host.com",
        accountId = "acc-1"
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            PreferencesManager(context).saveSession(
                server = session.server,
                email = session.email,
                accountId = session.accountId
            )
        }
        PushNavigationStore.clear()
    }

    @Test
    fun pushFromLoginRoute_navigatesToMessagesInbox() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferencesManager = PreferencesManager(context)
        val resolvePushNavigationUseCase = ResolvePushNavigationUseCase()

        composeRule.setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val pendingPushTarget by PushNavigationStore.pendingTarget.collectAsState()
                LaunchedEffect(Unit) {
                    PushNavigationStore.publish(
                        PushMessageTarget(
                            messageId = "m-push-1",
                            server = session.server,
                            email = session.email,
                            accountId = session.accountId
                        )
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.Login,
                ) {
                    composable(AppRoutes.Login) {
                        Text("Login", modifier = Modifier.testTag("login-screen"))
                    }
                    composable(AppRoutes.MessagesPattern) {
                        Text("Messages", modifier = Modifier.testTag("messages-screen"))
                    }
                }
                RootPushNavigationEffect(
                    navController = navController,
                    currentRoute = currentBackStackEntry?.destination?.route,
                    pendingPushTarget = pendingPushTarget,
                    preferencesManager = preferencesManager,
                    resolvePushNavigationUseCase = resolvePushNavigationUseCase,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithTag("messages-screen").assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }
}
