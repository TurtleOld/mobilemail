package com.mobilemail.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.model.Folder
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

    private val jmapClient = JmapClient(server, email, password, accountId)
    private val repository = MailRepository(jmapClient)

    init {
        loadFolders()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val folders = repository.getFolders()
                val inbox = folders.firstOrNull { it.role == FolderRole.INBOX }
                
                _uiState.value = _uiState.value.copy(
                    folders = folders,
                    selectedFolder = inbox,
                    isLoading = false
                )
                
                inbox?.let { loadMessages(it.id) }
            } catch (e: Exception) {
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
                val messages = repository.getMessages(folderId)
                _uiState.value = _uiState.value.copy(
                    messages = messages,
                    isLoading = false
                )
            } catch (e: Exception) {
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
