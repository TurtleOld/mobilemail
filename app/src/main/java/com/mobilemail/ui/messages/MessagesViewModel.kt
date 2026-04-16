package com.mobilemail.ui.messages

import android.app.Application
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.paging.MailPagingSource
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.FolderRole
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.data.repository.MessageActionsRepository
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.data.sync.OfflineQueueManager
import com.mobilemail.ui.common.NotificationState
import com.mobilemail.ui.common.SnackbarDuration
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper
import com.mobilemail.data.common.fold
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

data class MessagesUiState(
    val folders: List<Folder> = emptyList(),
    val selectedFolder: Folder? = null,
    val messages: List<MessageListItem> = emptyList(),
    val selectedMessageId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentPosition: Int = 0,
    val pendingQueueCount: Int = 0,
    val queueAttentionCount: Int = 0,
    val hiddenMessageIds: Set<String> = emptySet(),
    val error: AppError? = null,
    val selectedMessageIds: Set<String> = emptySet(),
    val notification: NotificationState = NotificationState.None
)

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModel(
    private val server: String,
    private val email: String,
    private val accountId: String,
    private val database: com.mobilemail.data.local.database.AppDatabase? = null,
    private val application: Application? = null
) : ViewModel() {
    private val app: Application = requireNotNull(application) { "Application is required" }

    private data class PendingFolderAction(
        val id: String,
        val restoreState: MessagesUiState,
        val job: Job,
        val commit: suspend () -> com.mobilemail.data.common.Result<Unit>,
        val onQueued: suspend () -> Unit
    )

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState

    private val jmapClient: JmapApi = MailClientFactory.create(
        application = requireNotNull(application) { "Application is required" },
        server = server,
        email = email,
        accountId = accountId
    )
    
    private val repository = MailRepository(
        jmapClient = jmapClient,
        messageDao = database?.messageDao(),
        folderDao = database?.folderDao()
    )
    private val messageActionsRepository = MessageActionsRepository(jmapClient)
    private var pendingFolderAction: PendingFolderAction? = null
    private val pagingRefreshTrigger = MutableStateFlow(0)

    val pagedMessages: Flow<PagingData<MessageListItem>> = combine(
        _uiState.map { it.selectedFolder?.id }.distinctUntilChanged(),
        pagingRefreshTrigger
    ) { folderId, refreshVersion -> folderId to refreshVersion }
        .flatMapLatest { (folderId, _) ->
            if (folderId == null) {
                kotlinx.coroutines.flow.flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 25,
                        initialLoadSize = 50,
                        prefetchDistance = 10,
                        enablePlaceholders = false
                    ),
                    pagingSourceFactory = { MailPagingSource(repository, folderId) }
                ).flow
            }
        }
        .cachedIn(viewModelScope)

    init {
        observeQueueStats()
        try {
            loadFolders()
        } catch (e: Exception) {
            android.util.Log.e("MessagesViewModel", "Ошибка при инициализации", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = ErrorMapper.mapException(e)
            )
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getFolders().fold(
                onError = { e ->
                    android.util.Log.e("MessagesViewModel", "Ошибка загрузки папок", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorMapper.mapException(e)
                    )
                },
                onSuccess = { folders ->
                    val inbox = folders.firstOrNull { it.role == FolderRole.INBOX }
                    
                    _uiState.value = _uiState.value.copy(
                        folders = folders,
                        selectedFolder = inbox,
                        isLoading = false
                    )
                    
                    if (inbox != null) {
                        delay(50)
                        pagingRefreshTrigger.value += 1
                    } else {
                        android.util.Log.w("MessagesViewModel", "Inbox не найден, письма не будут загружены")
                    }
                }
            )
        }
    }

    fun selectFolder(folder: Folder) {
        _uiState.value = _uiState.value.copy(
            selectedFolder = folder,
            messages = emptyList(),
            selectedMessageId = null,
            hiddenMessageIds = emptySet(),
            currentPosition = 0,
            hasMore = true,
            selectedMessageIds = emptySet()
        )
        pagingRefreshTrigger.value += 1
    }

    private fun loadMessages(folderId: String, reset: Boolean = false) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val position = if (reset) 0 else currentState.currentPosition
            
            if (reset) {
                _uiState.value = currentState.copy(isLoading = true, currentPosition = 0)
            } else {
                if (!currentState.hasMore || currentState.isLoadingMore) return@launch
                _uiState.value = currentState.copy(isLoadingMore = true)
            }
            
            repository.getMessages(folderId, position = position).fold(
                onError = { e ->
                    android.util.Log.e("MessagesViewModel", "Ошибка загрузки писем", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = ErrorMapper.mapException(e)
                    )
                },
                onSuccess = { newMessages ->
                    val updatedMessages = if (reset) {
                        newMessages
                    } else {
                        currentState.messages + newMessages
                    }
                    val hasMore = newMessages.size >= 50
                    val newPosition = position + newMessages.size
                    
                    _uiState.value = _uiState.value.copy(
                        messages = updatedMessages,
                        selectedMessageId = updatedMessages.firstOrNull()?.id?.takeIf {
                            reset && currentState.selectedMessageId == null
                        } ?: _uiState.value.selectedMessageId?.takeIf { selectedId ->
                            updatedMessages.any { it.id == selectedId }
                        },
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = hasMore,
                        currentPosition = newPosition
                    )
                }
            )
        }
    }
    
    fun loadMoreMessages() {
        // Paging handles prefetch automatically.
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(hiddenMessageIds = emptySet())
        pagingRefreshTrigger.value += 1
        refreshFoldersPreservingSelection()
    }

    fun queueMarkRead(messageId: String, isRead: Boolean) {
        viewModelScope.launch {
            OfflineQueueManager.enqueueMarkRead(app, server, email, accountId, messageId, isRead)
            refreshQueueStats()
        }
    }

    fun queueStar(messageId: String, isStarred: Boolean) {
        viewModelScope.launch {
            OfflineQueueManager.enqueueToggleStar(app, server, email, accountId, messageId, isStarred)
            refreshQueueStats()
        }
    }
    
    fun removeMessage(messageId: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.filter { it.id != messageId },
            selectedMessageId = _uiState.value.selectedMessageId.takeIf { it != messageId },
            hiddenMessageIds = _uiState.value.hiddenMessageIds + messageId
        )
    }
    
    fun updateMessageReadStatus(messageId: String, isUnread: Boolean) {
        val currentState = _uiState.value
        
        val oldMessage = currentState.messages.find { it.id == messageId }
        val wasUnread = oldMessage?.flags?.unread == true
        
        viewModelScope.launch {
            repository.updateMessageReadStatus(messageId, isUnread)
        }
        
        val updatedMessages = currentState.messages.map { message ->
            if (message.id == messageId) {
                message.copy(flags = message.flags.copy(unread = isUnread))
            } else {
                message
            }
        }
        
        val selectedFolder = currentState.selectedFolder
        if (selectedFolder != null) {
            val currentUnreadCount = selectedFolder.unreadCount
            
            val newUnreadCount = when {
                isUnread && !wasUnread -> currentUnreadCount + 1
                !isUnread && wasUnread -> maxOf(0, currentUnreadCount - 1)
                else -> currentUnreadCount
            }
            
            val updatedFolders = currentState.folders.map { folder ->
                if (folder.id == selectedFolder.id) {
                    folder.copy(unreadCount = newUnreadCount)
                } else {
                    folder
                }
            }
            
            val updatedSelectedFolder = updatedFolders.find { it.id == selectedFolder.id }
            
            _uiState.value = currentState.copy(
                messages = updatedMessages,
                folders = updatedFolders,
                selectedFolder = updatedSelectedFolder
            )
        } else {
            android.util.Log.w("MessagesViewModel", "Выбранная папка не найдена, обновляем только список писем")
            _uiState.value = currentState.copy(
                messages = updatedMessages
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearNotification() {
        _uiState.value = _uiState.value.copy(notification = NotificationState.None)
    }

    fun deleteSelected() {
        val selectedIds = _uiState.value.selectedMessageIds
        if (selectedIds.isEmpty()) return
        scheduleFolderAction(
            messageIds = selectedIds,
            pendingMessage = if (selectedIds.size == 1) "Письмо будет удалено" else "Писем будет удалено: ${selectedIds.size}",
            commitAction = { messageId -> messageActionsRepository.deleteMessage(messageId) },
            enqueueAction = { messageId ->
                OfflineQueueManager.enqueueDelete(app, server, email, accountId, messageId)
            }
        )
    }

    fun selectMessage(messageId: String?) {
        _uiState.value = _uiState.value.copy(selectedMessageId = messageId)
        if (messageId != null) {
            val selectedMessage = _uiState.value.messages.find { it.id == messageId }
            if (selectedMessage?.flags?.unread == true) {
                updateMessageReadStatus(messageId, isUnread = false)
            }
        }
    }

    fun toggleMessageSelection(messageId: String) {
        val current = _uiState.value.selectedMessageIds
        val updated = if (current.contains(messageId)) current - messageId else current + messageId
        _uiState.value = _uiState.value.copy(selectedMessageIds = updated)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedMessageIds = emptySet())
    }

    fun updateVisibleMessages(messages: List<MessageListItem>) {
        val hiddenIds = _uiState.value.hiddenMessageIds
        _uiState.value = _uiState.value.copy(
            messages = messages.filterNot { it.id in hiddenIds }
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedMessageIds = _uiState.value.messages.map { it.id }.toSet()
        )
    }

    fun archiveSelected() {
        moveSelectedToRole(FolderRole.ARCHIVE)
    }

    fun reportSpamSelected() {
        moveSelectedToRole(FolderRole.SPAM)
    }

    fun moveSelected(toFolderId: String) {
        val currentState = _uiState.value
        val fromFolderId = currentState.selectedFolder?.id ?: run {
            _uiState.value = currentState.copy(
                notification = NotificationState.Snackbar(
                    message = "Не выбрана исходная папка",
                    duration = SnackbarDuration.Short
                )
            )
            return
        }
        val destinationFolder = currentState.folders.firstOrNull { it.id == toFolderId } ?: run {
            _uiState.value = currentState.copy(
                notification = NotificationState.Snackbar(
                    message = "Не удалось найти целевую папку",
                    duration = SnackbarDuration.Short
                )
            )
            return
        }
        if (fromFolderId == toFolderId) {
            _uiState.value = currentState.copy(
                notification = NotificationState.Snackbar(
                    message = "Письма уже находятся в папке ${destinationFolder.name}",
                    duration = SnackbarDuration.Short
                )
            )
            return
        }

        val selectedIds = currentState.selectedMessageIds.toList()
        if (selectedIds.isEmpty()) return

        scheduleFolderAction(
            messageIds = selectedIds.toSet(),
            pendingMessage = if (selectedIds.size == 1) {
                "Письмо перемещено в ${destinationFolder.name}"
            } else {
                "Подготовлено к перемещению: ${selectedIds.size}"
            },
            commitAction = { messageId ->
                messageActionsRepository.moveMessage(messageId, fromFolderId, toFolderId)
            },
            enqueueAction = { messageId ->
                OfflineQueueManager.enqueueMove(app, server, email, accountId, messageId, fromFolderId, toFolderId)
            }
        )
    }

    fun archiveMessage(messageId: String) {
        moveSingleToRole(messageId, FolderRole.ARCHIVE)
    }

    fun deleteMessageWithUndo(messageId: String) {
        scheduleFolderAction(
            messageIds = setOf(messageId),
            pendingMessage = "Письмо будет удалено",
            commitAction = { id -> messageActionsRepository.deleteMessage(id) },
            enqueueAction = { id ->
                OfflineQueueManager.enqueueDelete(app, server, email, accountId, id)
            }
        )
    }

    private fun moveSingleToRole(messageId: String, role: FolderRole) {
        val currentFolderId = _uiState.value.selectedFolder?.id ?: return
        val targetFolder = _uiState.value.folders.firstOrNull { it.role == role } ?: run {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = when (role) {
                        FolderRole.ARCHIVE -> "Папка Архив недоступна"
                        FolderRole.SPAM -> "Папка Спам недоступна"
                        else -> "Целевая папка недоступна"
                    },
                    duration = SnackbarDuration.Short
                )
            )
            return
        }

        scheduleFolderAction(
            messageIds = setOf(messageId),
            pendingMessage = if (role == FolderRole.ARCHIVE) {
                "Письмо архивировано"
            } else {
                "Письмо помечено как спам"
            },
            commitAction = { id ->
                messageActionsRepository.moveMessage(id, currentFolderId, targetFolder.id)
            },
            enqueueAction = { id ->
                OfflineQueueManager.enqueueMove(app, server, email, accountId, id, currentFolderId, targetFolder.id)
            }
        )
    }

    private fun moveSelectedToRole(role: FolderRole) {
        val targetFolder = _uiState.value.folders.firstOrNull { it.role == role } ?: run {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = when (role) {
                        FolderRole.ARCHIVE -> "Папка Архив недоступна"
                        FolderRole.SPAM -> "Папка Спам недоступна"
                        else -> "Целевая папка недоступна"
                    },
                    duration = SnackbarDuration.Short
                )
            )
            return
        }
        moveSelected(targetFolder.id)
    }

    private fun scheduleFolderAction(
        messageIds: Set<String>,
        pendingMessage: String,
        commitAction: suspend (String) -> com.mobilemail.data.common.Result<Boolean>,
        enqueueAction: suspend (String) -> Unit
    ) {
        if (messageIds.isEmpty()) return

        val existingAction = pendingFolderAction
        if (existingAction != null) {
            viewModelScope.launch {
                finalizePendingAction(existingAction)
                scheduleFolderAction(messageIds, pendingMessage, commitAction, enqueueAction)
            }
            return
        }

        val currentState = _uiState.value
        val removedMessages = currentState.messages.filter { it.id in messageIds }
        if (removedMessages.isEmpty()) return
        val removedUnreadCount = removedMessages.count { it.flags.unread }
        val updatedSelectedFolder = currentState.selectedFolder?.copy(
            unreadCount = maxOf(0, currentState.selectedFolder.unreadCount - removedUnreadCount)
        )
        val updatedFolders = currentState.folders.map { folder ->
            if (folder.id == currentState.selectedFolder?.id && updatedSelectedFolder != null) updatedSelectedFolder else folder
        }
        val restoreState = currentState.copy(notification = NotificationState.None)
        val actionId = UUID.randomUUID().toString()

        val job = viewModelScope.launch {
            delay(5000)
            pendingFolderAction?.takeIf { it.id == actionId }?.let { finalizePendingAction(it) }
        }

        pendingFolderAction = PendingFolderAction(
            id = actionId,
            restoreState = restoreState,
            job = job,
            commit = {
                var failure: Throwable? = null
                messageIds.forEach { messageId ->
                    commitAction(messageId).fold(
                        onError = { error ->
                            if (failure == null) failure = error
                        },
                        onSuccess = {}
                    )
                }
                if (failure != null) {
                    com.mobilemail.data.common.Result.Error(failure!!)
                } else {
                    com.mobilemail.data.common.Result.Success(Unit)
                }
            },
            onQueued = {
                messageIds.forEach { enqueueAction(it) }
            }
        )

        _uiState.value = currentState.copy(
            messages = currentState.messages.filterNot { it.id in messageIds },
            selectedMessageIds = currentState.selectedMessageIds - messageIds,
            selectedMessageId = currentState.selectedMessageId.takeIf { it !in messageIds },
            hiddenMessageIds = currentState.hiddenMessageIds + messageIds,
            selectedFolder = updatedSelectedFolder,
            folders = updatedFolders,
            notification = NotificationState.Snackbar(
                message = pendingMessage,
                actionLabel = "Отменить",
                onAction = { undoPendingAction(actionId) },
                duration = SnackbarDuration.Long
            )
        )
    }

    private suspend fun finalizePendingAction(action: PendingFolderAction) {
        pendingFolderAction = null
        when (val result = action.commit()) {
            is com.mobilemail.data.common.Result.Success<*> -> {
                refreshFoldersPreservingSelection()
                _uiState.value.selectedFolder?.let { loadMessages(it.id, reset = true) }
            }
            is com.mobilemail.data.common.Result.Error -> {
                if (OfflineQueueManager.shouldQueue(result.exception)) {
                    action.onQueued()
                    refreshFoldersPreservingSelection()
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = "Нет сети. Действие поставлено в очередь и будет повторено автоматически.",
                            duration = SnackbarDuration.Long
                        )
                    )
                } else {
                    _uiState.value = action.restoreState.copy(
                        notification = NotificationState.Snackbar(
                            message = "Не удалось синхронизировать изменения: ${ErrorMapper.mapException(result.exception).getUserMessage()}",
                            duration = SnackbarDuration.Long
                        )
                    )
                }
            }
        }
    }

    private fun undoPendingAction(actionId: String) {
        val action = pendingFolderAction?.takeIf { it.id == actionId } ?: return
        action.job.cancel()
        pendingFolderAction = null
        _uiState.value = action.restoreState.copy(
            notification = NotificationState.Snackbar(
                message = "Действие отменено",
                duration = SnackbarDuration.Short
            )
        )
    }

    private fun refreshFoldersPreservingSelection() {
        viewModelScope.launch {
            val queueSummary = OfflineQueueManager.processPending(app)
            refreshQueueStats()
            repository.getFolders().fold(
                onError = { },
                onSuccess = { folders ->
                    val selectedFolderId = _uiState.value.selectedFolder?.id
                    _uiState.value = _uiState.value.copy(
                        folders = folders,
                        selectedFolder = folders.firstOrNull { it.id == selectedFolderId } ?: _uiState.value.selectedFolder,
                        notification = when {
                            queueSummary.processedCount > 0 -> NotificationState.Snackbar(
                                message = "Синхронизировано операций: ${queueSummary.processedCount}",
                                duration = SnackbarDuration.Short
                            )
                            queueSummary.permanentFailedCount > 0 -> NotificationState.Snackbar(
                                message = "Есть операции, требующие внимания: ${queueSummary.permanentFailedCount}",
                                duration = SnackbarDuration.Long
                            )
                            queueSummary.pendingCount > 0 -> NotificationState.Snackbar(
                                message = "Ожидают синхронизации: ${queueSummary.pendingCount}",
                                duration = SnackbarDuration.Short
                            )
                            else -> _uiState.value.notification
                        }
                    )
                }
            )
        }
    }

    private fun observeQueueStats() {
        viewModelScope.launch {
            OfflineQueueManager.observeAll(app).collect { operations ->
                _uiState.value = _uiState.value.copy(
                    pendingQueueCount = operations.count {
                        it.status == OfflineQueueManager.STATUS_PENDING || it.status == OfflineQueueManager.STATUS_FAILED
                    },
                    queueAttentionCount = operations.count {
                        it.status == OfflineQueueManager.STATUS_PERMANENT_FAILED
                    }
                )
            }
        }
    }

    private suspend fun refreshQueueStats() {
        val stats = OfflineQueueManager.getStats(app)
        _uiState.value = _uiState.value.copy(
            pendingQueueCount = stats.pendingCount + stats.failedCount,
            queueAttentionCount = stats.permanentFailedCount
        )
    }
}
