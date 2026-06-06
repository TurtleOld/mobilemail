package com.mobilemail.ui.outbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.ui.common.FeatureScreenEffects
import com.mobilemail.ui.common.rememberFeatureScreenSnackbarHostState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboxScreen(
    viewModel: OutboxViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = rememberFeatureScreenSnackbarHostState()

    FeatureScreenEffects(
        uiState = uiState,
        onErrorConsumed = viewModel::clearError,
        onNotificationConsumed = viewModel::clearNotification,
        snackbarHostState = snackbarHostState,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Очередь синхронизации") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                    } else {
                        if (uiState.stats.failedCount + uiState.stats.permanentFailedCount > 0) {
                            IconButton(onClick = viewModel::retryFailed) {
                                Icon(Icons.Default.Warning, contentDescription = "Повторить неуспешные")
                            }
                        }
                        IconButton(onClick = viewModel::retryNow) {
                            Icon(Icons.Default.Refresh, contentDescription = "Повторить")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.operations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Очередь пуста", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Отложенные отправки и действия появятся здесь")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QueueStatChip(label = "В очереди", count = uiState.stats.pendingCount)
                    QueueStatChip(label = "Сбои", count = uiState.stats.failedCount)
                    QueueStatChip(label = "Требуют внимания", count = uiState.stats.permanentFailedCount)
                }
                if (uiState.stats.failedCount + uiState.stats.permanentFailedCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = viewModel::retryFailed, label = { Text("Повторить неуспешные") })
                        AssistChip(onClick = viewModel::clearFailed, label = { Text("Очистить неуспешные") })
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.operations, key = { it.id }) { operation ->
                        PendingOperationCard(
                            operation = operation,
                            onRemove = { viewModel.remove(operation.id) },
                            onRetry = { viewModel.retryOperation(operation.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingOperationCard(
    operation: PendingOperationEntity,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { SimpleDateFormat("dd.MM.yyyy HH:mm", locale) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(operationTypeLabel(operation.type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row {
                    if (operation.status == "failed" || operation.status == "permanent_failed") {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = "Повторить операцию")
                        }
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить из очереди")
                    }
                }
            }
            Text("Статус: ${operationStatusLabel(operation.status)}")
            Text("Попыток: ${operation.attemptCount}")
            Text("Создано: ${formatter.format(Date(operation.createdAt))}")
            if (!operation.lastError.isNullOrBlank()) {
                HorizontalDivider()
                Text(operation.lastError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun QueueStatChip(label: String, count: Int) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text("$label: $count") }
    )
}

private fun operationTypeLabel(type: String): String = when (type) {
    "send" -> "Отправка письма"
    "move" -> "Перемещение письма"
    "delete" -> "Удаление письма"
    "mark_read" -> "Изменение прочитанности"
    "toggle_star" -> "Изменение избранного"
    else -> type
}

private fun operationStatusLabel(status: String): String = when (status) {
    "pending" -> "Ожидает"
    "running" -> "Выполняется"
    "failed" -> "Повтор будет выполнен"
    "permanent_failed" -> "Требует внимания"
    else -> status
}
