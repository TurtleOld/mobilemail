package com.mobilemail.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration as MaterialSnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberFeatureScreenSnackbarHostState(): SnackbarHostState = remember { SnackbarHostState() }

@Composable
fun FeatureScreenEffects(
    uiState: FeatureScreenUiState,
    onErrorConsumed: () -> Unit,
    onNotificationConsumed: () -> Unit,
    snackbarHostState: SnackbarHostState = rememberFeatureScreenSnackbarHostState(),
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        if (error is AppError.AuthError) return@LaunchedEffect
        scope.launch {
            snackbarHostState.showSnackbar(
                message = error.getUserMessage(),
                duration = MaterialSnackbarDuration.Long
            )
            onErrorConsumed()
        }
    }

    LaunchedEffect(uiState.notification) {
        when (val notification = uiState.notification) {
            is NotificationState.Snackbar -> {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = notification.message,
                        actionLabel = notification.actionLabel,
                        duration = when (notification.duration) {
                            SnackbarDuration.Short -> MaterialSnackbarDuration.Short
                            SnackbarDuration.Long -> MaterialSnackbarDuration.Long
                            SnackbarDuration.Indefinite -> MaterialSnackbarDuration.Indefinite
                        }
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        notification.onAction?.invoke()
                    }
                    onNotificationConsumed()
                }
            }
            else -> Unit
        }
    }
}
