package com.mobilemail.ui.outbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.data.sync.OfflineQueueManager
import com.mobilemail.data.sync.OfflineQueueStats
import com.mobilemail.data.sync.OfflineQueueWorker
import com.mobilemail.ui.common.NotificationState
import com.mobilemail.ui.common.SnackbarDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OutboxUiState(
    val operations: List<PendingOperationEntity> = emptyList(),
    val stats: OfflineQueueStats = OfflineQueueStats(0, 0, 0, 0),
    val notification: NotificationState = NotificationState.None,
    val isProcessing: Boolean = false
)

class OutboxViewModel(
    application: Application,
    private val server: String,
    private val email: String,
    private val accountId: String
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(OutboxUiState())
    val uiState: StateFlow<OutboxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            OfflineQueueManager.observeAll(getApplication()).collect { operations ->
                val accountOperations = operations.filter {
                    it.server == server && it.email == email && it.accountId == accountId
                }
                val stats = OfflineQueueStats(
                    pendingCount = accountOperations.count { it.status == OfflineQueueManager.STATUS_PENDING },
                    failedCount = accountOperations.count { it.status == OfflineQueueManager.STATUS_FAILED },
                    permanentFailedCount = accountOperations.count { it.status == OfflineQueueManager.STATUS_PERMANENT_FAILED },
                    totalCount = accountOperations.size
                )
                _uiState.value = _uiState.value.copy(operations = accountOperations, stats = stats)
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

    fun retryOperation(id: Long) {
        viewModelScope.launch {
            OfflineQueueManager.retryOne(getApplication(), id)
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Операция поставлена на повтор",
                    duration = SnackbarDuration.Short
                )
            )
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            OfflineQueueManager.retryAllFailed(getApplication())
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Все неуспешные операции поставлены на повтор",
                    duration = SnackbarDuration.Short
                )
            )
        }
    }

    fun clearFailed() {
        viewModelScope.launch {
            OfflineQueueManager.clearFailed(getApplication())
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Неуспешные операции очищены",
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
