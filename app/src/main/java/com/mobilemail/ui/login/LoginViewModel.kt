package com.mobilemail.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.model.Account
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LoginUiState(
    val server: String = "",
    val login: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val account: Account? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        viewModelScope.launch {
            val savedServer = preferencesManager.getServerUrl()
            if (!savedServer.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(server = savedServer)
            }
        }
    }

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
            _uiState.value = state.copy(error = "Заполните все поля")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val normalizedServer = state.server.trimEnd('/')
                Log.d("LoginViewModel", "Попытка входа: сервер=$normalizedServer, email=${state.login}")
                
                preferencesManager.saveServerUrl(normalizedServer)
                
                val jmapClient = JmapClient.getOrCreate(
                    baseUrl = normalizedServer,
                    email = state.login,
                    password = state.password,
                    accountId = state.login
                )

                val repository = MailRepository(jmapClient)
                val account = repository.getAccount()

                if (account != null) {
                    Log.d("LoginViewModel", "Вход успешен, account: ${account.id}")
                    _uiState.value = state.copy(
                        isLoading = false,
                        account = account
                    )
                    onSuccess(account)
                } else {
                    Log.w("LoginViewModel", "Не удалось получить аккаунт")
                    _uiState.value = state.copy(
                        isLoading = false,
                        error = "Неверные учетные данные или сервер недоступен"
                    )
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Ошибка входа", e)
                val errorMessage = when {
                    e.message?.contains("connect", ignoreCase = true) == true -> 
                        "Не удалось подключиться к серверу. Проверьте адрес и доступность сервера."
                    e.message?.contains("401") == true || e.message?.contains("403") == true ->
                        "Неверные учетные данные"
                    e.message?.contains("404") == true ->
                        "Сервер не найден. Проверьте адрес сервера."
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Превышено время ожидания. Проверьте подключение к сети."
                    else -> e.message ?: "Ошибка соединения: ${e.javaClass.simpleName}"
                }
                _uiState.value = state.copy(
                    isLoading = false,
                    error = errorMessage
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
