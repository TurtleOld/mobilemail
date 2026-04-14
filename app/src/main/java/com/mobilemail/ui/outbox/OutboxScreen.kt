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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.ui.common.NotificationState
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.notification) {
        when (val notification = uiState.notification) {
            is NotificationState.Snackbar -> {
                snackbarHostState.showSnackbar(notification.message)
                viewModel.clearNotification()
            }
            else -> Unit
        }
    }

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.operations, key = { it.id }) { operation ->
                    PendingOperationCard(operation = operation, onRemove = { viewModel.remove(operation.id) })
                }
            }
        }
    }
}

@Composable
private fun PendingOperationCard(
    operation: PendingOperationEntity,
    onRemove: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(operationTypeLabel(operation.type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить из очереди")
                }
            }
            Text("Статус: ${operation.status}")
            Text("Попыток: ${operation.attemptCount}")
            Text("Создано: ${formatter.format(Date(operation.createdAt))}")
            if (!operation.lastError.isNullOrBlank()) {
                Divider()
                Text(operation.lastError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun operationTypeLabel(type: String): String = when (type) {
    "send" -> "Отправка письма"
    "move" -> "Перемещение письма"
    "delete" -> "Удаление письма"
    else -> type
}
