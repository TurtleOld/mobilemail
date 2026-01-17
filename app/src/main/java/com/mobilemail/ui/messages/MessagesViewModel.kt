package com.mobilemail.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.FolderRole
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MessagesUiState(
    val folders: List<Folder> = emptyList(),
    val selectedFolder: Folder? = null,
    val messages: List<MessageListItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MessagesViewModel(
    private val server: String,
    private val email: String,
    private val password: String,
    private val accountId: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState

    private val jmapClient = JmapClient.getOrCreate(server, email, password, accountId)
    private val repository = MailRepository(jmapClient)

    init {
        android.util.Log.d("MessagesViewModel", "Инициализация ViewModel: server=$server, email=$email, accountId=$accountId")
        loadFolders()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            android.util.Log.d("MessagesViewModel", "Начало загрузки папок")
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val folders = repository.getFolders()
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
                    loadMessages(inbox.id)
                } else {
                    android.util.Log.w("MessagesViewModel", "Inbox не найден, письма не будут загружены")
                }
            } catch (e: Exception) {
                android.util.Log.e("MessagesViewModel", "Ошибка загрузки папок", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки папок"
                )
            }
        }
    }

    fun selectFolder(folder: Folder) {
        _uiState.value = _uiState.value.copy(
            selectedFolder = folder,
            messages = emptyList()
        )
        loadMessages(folder.id)
    }

    private fun loadMessages(folderId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                android.util.Log.d("MessagesViewModel", "Загрузка писем для папки: $folderId")
                val messages = repository.getMessages(folderId)
                android.util.Log.d("MessagesViewModel", "Получено писем: ${messages.size}")
                android.util.Log.d("MessagesViewModel", "Первое письмо: ${messages.firstOrNull()?.subject}")
                val newState = _uiState.value.copy(
                    messages = messages,
                    isLoading = false
                )
                android.util.Log.d("MessagesViewModel", "Обновление состояния: messages=${newState.messages.size}, isLoading=${newState.isLoading}")
                _uiState.value = newState
            } catch (e: Exception) {
                android.util.Log.e("MessagesViewModel", "Ошибка загрузки писем", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки писем"
                )
            }
        }
    }

    fun refresh() {
        _uiState.value.selectedFolder?.let { loadMessages(it.id) }
    }
}
