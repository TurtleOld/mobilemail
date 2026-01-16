package com.mobilemail.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.model.Account
import com.mobilemail.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val server: String = '',
    val login: String = '',
    val password: String = '',
    val isLoading: Boolean = false,
    val error: String? = null,
    val account: Account? = null
)

class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun updateServer(server: String) {
        _uiState.value = _uiState.value.copy(server = server)
    }

    fun updateLogin(login: String) {
        _uiState.value = _uiState.value.copy(login = login)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun login(onSuccess: (Account) -> Unit) {
        val state = _uiState.value
        if (state.server.isBlank() || state.login.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = 'Заполните все поля')
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val normalizedServer = state.server.trimEnd('/')
                val jmapClient = JmapClient(
                    baseUrl = normalizedServer,
                    email = state.login,
                    password = state.password,
                    accountId = state.login
                )

                val repository = MailRepository(jmapClient)
                val account = repository.getAccount()

                if (account != null) {
                    _uiState.value = state.copy(
                        isLoading = false,
                        account = account
                    )
                    onSuccess(account)
                } else {
                    _uiState.value = state.copy(
                        isLoading = false,
                        error = 'Неверные учетные данные'
                    )
                }
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    error = e.message ?: 'Ошибка соединения'
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
