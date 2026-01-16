package com.mobilemail.ui.messagedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.model.MessageDetail
import com.mobilemail.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MessageDetailUiState(
    val message: MessageDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class MessageDetailViewModel(
    private val server: String,
    private val email: String,
    private val password: String,
    private val accountId: String,
    private val messageId: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessageDetailUiState())
    val uiState: StateFlow<MessageDetailUiState> = _uiState

    private val jmapClient = JmapClient(server, email, password, accountId)
    private val repository = MailRepository(jmapClient)

    init {
        loadMessage()
    }

    private fun loadMessage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val message = repository.getMessage(messageId)
                _uiState.value = _uiState.value.copy(
                    message = message,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: 'Ошибка загрузки письма'
                )
            }
        }
    }
}
