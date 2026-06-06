package com.mobilemail.ui.navigation

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    val pendingPushTarget by PushNavigationStore.pendingTarget.collectAsStateWithLifecycle()

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
