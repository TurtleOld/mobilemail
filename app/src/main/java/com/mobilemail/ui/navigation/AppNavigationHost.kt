package com.mobilemail.ui.navigation

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mobilemail.notifications.PushNavigationStore

private const val TAG = "AppNavigationHost"

@Composable
fun AppNavigationHost(
    dependencies: AppNavigationDependencies,
    startDestination: String,
    intent: Intent?,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val privacyScreenProtection by dependencies.preferencesManager.privacyScreenProtection
        .collectAsState(initial = true)
    val pendingPushTarget by PushNavigationStore.pendingTarget.collectAsState()

    PrivacyScreenProtectionEffect(enabled = privacyScreenProtection)

    RootPushNavigationEffect(
        navController = navController,
        currentRoute = currentBackStackEntry?.destination?.route,
        pendingPushTarget = pendingPushTarget,
        preferencesManager = dependencies.preferencesManager,
        resolvePushNavigationUseCase = dependencies.resolvePushNavigationUseCase,
    )

    AppNavGraph(
        navController = navController,
        startDestination = startDestination,
        dependencies = dependencies,
        modifier = modifier,
    )

    IncomingDeepLinkEffect(navController = navController, intent = intent)
}

@Composable
private fun PrivacyScreenProtectionEffect(enabled: Boolean) {
    val view = LocalView.current
    LaunchedEffect(enabled) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
private fun IncomingDeepLinkEffect(
    navController: NavHostController,
    intent: Intent?,
) {
    val data = intent?.data ?: return
    val request = NavDeepLinkRequest.Builder.fromUri(data).build()
    if (!navController.graph.hasDeepLink(request)) {
        Log.w(TAG, "Deep link not in graph, ignoring: $data")
        return
    }
    LaunchedEffect(data, intent) {
        try {
            navController.handleDeepLink(intent)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Deep link not matched, ignoring: $data", e)
        }
    }
}
