package com.mobilemail.ui.messages

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.jmap.JmapOAuthClient
import com.mobilemail.data.oauth.OAuthDiscovery
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.FolderRole
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.data.repository.MailRepository
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
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentPosition: Int = 0,
    val error: AppError? = null
)

class MessagesViewModel(
    private val server: String,
    private val email: String,
    private val password: String,
    private val accountId: String,
    private val database: com.mobilemail.data.local.database.AppDatabase? = null,
    private val application: Application? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState

    private val jmapClient: Any = if (password.isBlank() && application != null) {
        val tokenStore = TokenStore(application)
        val tokens = tokenStore.getTokens(server, email)
        if (tokens != null && tokens.isValid()) {
            kotlinx.coroutines.runBlocking {
                try {
                    val httpClient = OAuthDiscovery.createClient()
                    val discovery = OAuthDiscovery(httpClient)
                    val discoveryUrl = "$server/.well-known/oauth-authorization-server"
                    val metadata = discovery.discover(discoveryUrl)
                    JmapOAuthClient.getOrCreate(
                        baseUrl = server,
                        email = email,
                        accountId = accountId,
                        tokenStore = tokenStore,
                        metadata = metadata,
                        clientId = "mail-client"
                    )
                } catch (e: Exception) {
                    android.util.Log.e("MessagesViewModel", "Ошибка создания OAuth клиента", e)
                    JmapClient.getOrCreate(server, email, "", accountId)
                }
            }
        } else {
            JmapClient.getOrCreate(server, email, password, accountId)
        }
    } else {
        JmapClient.getOrCreate(server, email, password, accountId)
    }
    
    private val repository = MailRepository(
        jmapClient = jmapClient,
        messageDao = database?.messageDao(),
        folderDao = database?.folderDao()
    )

    init {
        android.util.Log.d("MessagesViewModel", "Инициализация ViewModel: server=$server, email=$email, accountId=$accountId")
        android.util.Log.d("MessagesViewModel", "JmapClient создан, начинаем загрузку папок")
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
            android.util.Log.d("MessagesViewModel", "Начало загрузки папок")
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
                    android.util.Log.d("MessagesViewModel", "Получено папок: ${folders.size}")
                    val inbox = folders.firstOrNull { it.role == FolderRole.INBOX }
                    android.util.Log.d("MessagesViewModel", "Inbox найден: ${inbox?.id}, имя: ${inbox?.name}")
                    
                    _uiState.value = _uiState.value.copy(
                        folders = folders,
                        selectedFolder = inbox,
                        isLoading = false
                    )
                    
                    if (inbox != null) {
                        android.util.Log.d("MessagesViewModel", "Загрузка писем для inbox: ${inbox.id}")
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
            currentPosition = 0,
            hasMore = true
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
                    android.util.Log.d("MessagesViewModel", "Получено писем: ${newMessages.size}")
                    val updatedMessages = if (reset) {
                        newMessages
                    } else {
                        currentState.messages + newMessages
                    }
                    val hasMore = newMessages.size >= 50
                    val newPosition = position + newMessages.size
                    
                    _uiState.value = _uiState.value.copy(
                        messages = updatedMessages,
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
            messages = _uiState.value.messages.filter { it.id != messageId }
        )
    }
    
    fun updateMessageReadStatus(messageId: String, isUnread: Boolean) {
        val currentState = _uiState.value
        android.util.Log.d("MessagesViewModel", "Обновление статуса прочитанности: messageId=$messageId, isUnread=$isUnread")
        
        val oldMessage = currentState.messages.find { it.id == messageId }
        val wasUnread = oldMessage?.flags?.unread == true
        android.util.Log.d("MessagesViewModel", "Старый статус: wasUnread=$wasUnread")
        
        // Обновляем кэш в MailRepository
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
        
        // Обновляем счетчик непрочитанных во всех папках, где может быть это письмо
        // Но так как мы не знаем точно, в какой папке письмо, обновляем только выбранную
        val selectedFolder = currentState.selectedFolder
        if (selectedFolder != null) {
            val currentUnreadCount = selectedFolder.unreadCount
            android.util.Log.d("MessagesViewModel", "Текущий счетчик непрочитанных в папке ${selectedFolder.name}: $currentUnreadCount")
            
            val newUnreadCount = when {
                isUnread && !wasUnread -> {
                    val newCount = currentUnreadCount + 1
                    android.util.Log.d("MessagesViewModel", "Увеличиваем счетчик: $currentUnreadCount -> $newCount")
                    newCount
                }
                !isUnread && wasUnread -> {
                    val newCount = maxOf(0, currentUnreadCount - 1)
                    android.util.Log.d("MessagesViewModel", "Уменьшаем счетчик: $currentUnreadCount -> $newCount")
                    newCount
                }
                else -> {
                    android.util.Log.d("MessagesViewModel", "Счетчик не изменяется: $currentUnreadCount")
                    currentUnreadCount
                }
            }
            
            val updatedFolders = currentState.folders.map { folder ->
                if (folder.id == selectedFolder.id) {
                    folder.copy(unreadCount = newUnreadCount)
                } else {
                    folder
                }
            }
            
            val updatedSelectedFolder = updatedFolders.find { it.id == selectedFolder.id }
            android.util.Log.d("MessagesViewModel", "Новый счетчик непрочитанных: ${updatedSelectedFolder?.unreadCount}")
            
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
    }
