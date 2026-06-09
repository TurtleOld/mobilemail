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
    isPinLocked: Boolean,
    onPinUnlocked: () -> Unit,
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

    PinLockNavigationEffect(
        navController = navController,
        isPinLocked = isPinLocked,
        currentRoute = currentBackStackEntry?.destination?.route,
    )

    AppNavGraph(
        navController = navController,
        startDestination = startDestination,
        dependencies = dependencies,
        onPinUnlocked = onPinUnlocked,
        modifier = modifier,
    )

    IncomingDeepLinkEffect(
        navController = navController,
        intent = intent,
        isPinLocked = isPinLocked,
    )
}

@Composable
private fun PinLockNavigationEffect(
    navController: NavHostController,
    isPinLocked: Boolean,
    currentRoute: String?,
) {
    LaunchedEffect(isPinLocked, currentRoute) {
        if (isPinLocked && currentRoute != AppRoutes.PinLock) {
            navController.navigate(AppRoutes.PinLock) {
                launchSingleTop = true
            }
        }
    }
}

@Composable
private fun IncomingDeepLinkEffect(
    navController: NavHostController,
    intent: Intent?,
    isPinLocked: Boolean,
) {
    if (isPinLocked) return
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
