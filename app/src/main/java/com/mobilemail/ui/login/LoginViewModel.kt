package com.mobilemail.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.jmap.TwoFactorRequiredException
import com.mobilemail.data.model.Account
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.data.security.CredentialManager
import com.mobilemail.data.common.fold
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val server: String = "",
    val login: String = "",
    val password: String = "",
    val totpCode: String = "",
    val requiresTwoFactor: Boolean = false,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val account: Account? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)
    private val credentialManager = CredentialManager(application)
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        viewModelScope.launch {
            val savedSession = preferencesManager.getSavedSession()
            if (savedSession != null) {
                val savedPassword = credentialManager.getPassword(savedSession.server, savedSession.email)
                if (!savedPassword.isNullOrBlank()) {
                    Log.d("LoginViewModel", "Найдена сохраненная сессия: ${savedSession.email}")
                    _uiState.value = _uiState.value.copy(
                        server = savedSession.server,
                        login = savedSession.email
                    )
                    // Автоматический вход
                    autoLogin(savedSession.server, savedSession.email, savedPassword, savedSession.accountId)
                } else {
                    Log.d("LoginViewModel", "Сохраненная сессия найдена, но пароль отсутствует")
                    _uiState.value = _uiState.value.copy(server = savedSession.server, login = savedSession.email)
                }
            } else {
                val savedServer = preferencesManager.getServerUrl()
                if (!savedServer.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(server = savedServer)
                    Log.d("LoginViewModel", "Загружен сохраненный адрес сервера: $savedServer")
                } else {
                    Log.d("LoginViewModel", "Сохраненный адрес сервера не найден")
                }
            }
        }
    }

    private fun autoLogin(server: String, email: String, password: String, accountId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val normalizedServer = server.trim().trimEnd('/')
                val jmapClient = JmapClient.getOrCreate(
                    baseUrl = normalizedServer,
                    email = email,
                    password = password,
                    accountId = accountId
                )

                val repository = MailRepository(jmapClient)
                repository.getAccount().fold(
                    onError = { e ->
                        Log.w("LoginViewModel", "Автовход не удался", e)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = ErrorMapper.mapException(e)
                        )
                        // Очищаем невалидную сессию
                        preferencesManager.clearSession()
                        credentialManager.clearCredentials(email)
                    },
                    onSuccess = { account ->
                        Log.d("LoginViewModel", "Автовход успешен")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            account = account
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Ошибка автовхода", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorMapper.mapException(e)
                )
                preferencesManager.clearSession()
                credentialManager.clearCredentials(email)
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
    
    fun updateTotpCode(totpCode: String) {
        _uiState.value = _uiState.value.copy(totpCode = totpCode)
    }
    
    fun clearTwoFactorRequirement() {
        _uiState.value = _uiState.value.copy(requiresTwoFactor = false, totpCode = "")
    }

    fun login(onSuccess: (Account) -> Unit) {
        val state = _uiState.value
        
        if (state.server.isBlank()) {
            _uiState.value = state.copy(error = AppError.UnknownError("Введите адрес сервера"))
            return
        }
        
        if (state.login.isBlank()) {
            _uiState.value = state.copy(error = AppError.UnknownError("Введите логин"))
            return
        }
        
        if (state.password.isBlank()) {
            _uiState.value = state.copy(error = AppError.UnknownError("Введите пароль"))
            return
        }
        
        if (state.requiresTwoFactor && state.totpCode.isBlank()) {
            _uiState.value = state.copy(error = AppError.UnknownError("Введите код двухфакторной авторизации"))
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val normalizedServer = state.server.trim().trimEnd('/')
                if (normalizedServer.isBlank()) {
                    _uiState.value = state.copy(
                        isLoading = false,
                        error = AppError.UnknownError("Адрес сервера не может быть пустым")
                    )
                    return@launch
                }
                
                Log.d("LoginViewModel", "Попытка входа с адресом сервера из поля ввода: сервер=$normalizedServer, email=${state.login}, requiresTwoFactor=${state.requiresTwoFactor}")
                
                val jmapClient = JmapClient.getOrCreate(
                    baseUrl = normalizedServer,
                    email = state.login,
                    password = state.password,
                    accountId = state.login
                )
                
                if (state.requiresTwoFactor && state.totpCode.isNotBlank()) {
                    try {
                        jmapClient.getSessionWithTotp(state.totpCode)
                    } catch (e: TwoFactorRequiredException) {
                        _uiState.value = state.copy(
                            isLoading = false,
                            error = AppError.TwoFactorRequired("Неверный код двухфакторной авторизации")
                        )
                        return@launch
                    }
                }

                val repository = MailRepository(jmapClient)
                repository.getAccount().fold(
                    onError = { e ->
                        Log.w("LoginViewModel", "Не удалось получить аккаунт", e)
                        if (e is TwoFactorRequiredException) {
                            _uiState.value = state.copy(
                                isLoading = false,
                                requiresTwoFactor = true,
                                error = AppError.TwoFactorRequired()
                            )
                        } else {
                            _uiState.value = state.copy(
                                isLoading = false,
                                error = ErrorMapper.mapException(e)
                            )
                        }
                    },
                    onSuccess = { account ->
                        Log.d("LoginViewModel", "Вход успешен, account: ${account.id}")
                        preferencesManager.saveSession(normalizedServer, state.login, account.id)
                        credentialManager.saveCredentials(normalizedServer, state.login, state.password)
                        Log.d("LoginViewModel", "Сессия сохранена")
                        _uiState.value = state.copy(
                            isLoading = false,
                            account = account,
                            requiresTwoFactor = false,
                            totpCode = ""
                        )
                        onSuccess(account)
                    }
                )
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Ошибка входа", e)
                if (e is TwoFactorRequiredException) {
                    _uiState.value = state.copy(
                        isLoading = false,
                        requiresTwoFactor = true,
                        error = AppError.TwoFactorRequired()
                    )
                } else {
                    _uiState.value = state.copy(
                        isLoading = false,
                        error = ErrorMapper.mapException(e)
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
