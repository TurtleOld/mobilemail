package com.mobilemail.ui.outbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.data.sync.OfflineQueueManager
import com.mobilemail.data.sync.OfflineQueueWorker
import com.mobilemail.ui.common.NotificationState
import com.mobilemail.ui.common.SnackbarDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OutboxUiState(
    val operations: List<PendingOperationEntity> = emptyList(),
    val notification: NotificationState = NotificationState.None,
    val isProcessing: Boolean = false
)

class OutboxViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(OutboxUiState())
    val uiState: StateFlow<OutboxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            OfflineQueueManager.observeAll(getApplication()).collect { operations ->
                _uiState.value = _uiState.value.copy(operations = operations)
            }
        }
    }

    fun retryNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val summary = OfflineQueueManager.processPending(getApplication())
            OfflineQueueWorker.scheduleNow(getApplication())
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                notification = NotificationState.Snackbar(
                    message = when {
                        summary.processedCount > 0 -> "Синхронизировано операций: ${summary.processedCount}"
                        summary.pendingCount > 0 -> "Ожидают синхронизации: ${summary.pendingCount}"
                        else -> "Очередь пуста"
                    },
                    duration = SnackbarDuration.Short
                )
            )
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            OfflineQueueManager.remove(getApplication(), id)
        }
    }

    fun clearNotification() {
        _uiState.value = _uiState.value.copy(notification = NotificationState.None)
    }
}
