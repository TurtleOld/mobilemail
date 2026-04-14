package com.mobilemail.ui.messages

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.FolderRole
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.data.repository.MessageActionsRepository
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.ui.common.NotificationState
import com.mobilemail.ui.common.SnackbarDuration
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper
import com.mobilemail.data.common.fold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MessagesUiState(
    val folders: List<Folder> = emptyList(),
    val selectedFolder: Folder? = null,
    val messages: List<MessageListItem> = emptyList(),
    val selectedMessageId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentPosition: Int = 0,
    val error: AppError? = null,
    val selectedMessageIds: Set<String> = emptySet(),
    val notification: NotificationState = NotificationState.None
)

class MessagesViewModel(
    private val server: String,
    private val email: String,
    private val accountId: String,
    private val database: com.mobilemail.data.local.database.AppDatabase? = null,
    private val application: Application? = null
) : ViewModel() {
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

    init {
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
                        delay(200)
                        loadMessages(inbox.id, reset = true)
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
            currentPosition = 0,
            hasMore = true,
            selectedMessageIds = emptySet()
        )
        loadMessages(folder.id, reset = true)
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
        _uiState.value.selectedFolder?.let { folder ->
            if (!_uiState.value.isLoadingMore && _uiState.value.hasMore) {
                loadMessages(folder.id, reset = false)
            }
        }
    }

    fun refresh() {
        _uiState.value.selectedFolder?.let { loadMessages(it.id, reset = true) }
    }
    
    fun removeMessage(messageId: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.filter { it.id != messageId },
            selectedMessageId = _uiState.value.selectedMessageId.takeIf { it != messageId }
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

    fun selectMessage(messageId: String?) {
        _uiState.value = _uiState.value.copy(selectedMessageId = messageId)
    }

    fun toggleMessageSelection(messageId: String) {
        val current = _uiState.value.selectedMessageIds
        val updated = if (current.contains(messageId)) current - messageId else current + messageId
        _uiState.value = _uiState.value.copy(selectedMessageIds = updated)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedMessageIds = emptySet())
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

        viewModelScope.launch {
            var movedCount = 0
            var lastError: Throwable? = null
            val movedIds = mutableSetOf<String>()
            selectedIds.forEach { messageId ->
                messageActionsRepository.moveMessage(messageId, fromFolderId, toFolderId).fold(
                    onError = { e -> lastError = e },
                    onSuccess = {
                        movedCount++
                        movedIds += messageId
                    }
                )
            }

            if (movedCount > 0) {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.filterNot { it.id in movedIds },
                    selectedMessageIds = _uiState.value.selectedMessageIds - movedIds,
                    notification = NotificationState.Snackbar(
                        message = if (movedCount == 1) {
                            "Письмо перемещено в ${destinationFolder.name}"
                        } else {
                            "Перемещено писем: $movedCount"
                        },
                        duration = SnackbarDuration.Short
                    )
                )
            }

            if (movedCount == 0 && lastError != null) {
                _uiState.value = _uiState.value.copy(
                    notification = NotificationState.Snackbar(
                        message = "Не удалось переместить письма: ${ErrorMapper.mapException(lastError!!).getUserMessage()}",
                        duration = SnackbarDuration.Long
                    )
                )
            }
        }
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
}
