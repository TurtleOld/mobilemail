package com.mobilemail.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapOAuthClient
import com.mobilemail.data.model.Account
import com.mobilemail.data.oauth.DeviceFlowClient
import com.mobilemail.data.oauth.DeviceFlowState
import com.mobilemail.data.oauth.OAuthDeviceFlowError
import com.mobilemail.data.oauth.OAuthDiscovery
import com.mobilemail.data.oauth.OAuthException
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.data.common.fold
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val server: String = "",
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val account: Account? = null,
    val requiresTwoFactor: Boolean = false,
    val totpCode: String = "",
    val oauthUserCode: String? = null,
    val oauthVerificationUri: String? = null,
    val oauthVerificationUriComplete: String? = null,
    val oauthExpiresAt: Long? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)
    private val tokenStore = TokenStore(application)
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState
    
    private var deviceFlowClient: DeviceFlowClient? = null
    private var discovery: OAuthDiscovery? = null
    
    companion object {
        private const val CLIENT_ID = "mail-client"
    }

    init {
        viewModelScope.launch {
            val savedSession = preferencesManager.getSavedSession()
            if (savedSession != null) {
                val tokenStore = TokenStore(application)
                val tokens = tokenStore.getTokens(savedSession.server, savedSession.email)
                if (tokens != null && tokens.isValid()) {
                    Log.d("LoginViewModel", "Найдена сохраненная OAuth сессия: ${savedSession.email}")
                    _uiState.value = _uiState.value.copy(server = savedSession.server)
                    autoOAuthLogin(savedSession.server, savedSession.email, savedSession.accountId)
                } else {
                    Log.d("LoginViewModel", "Сохраненная сессия найдена, но OAuth токены отсутствуют")
                    _uiState.value = _uiState.value.copy(server = savedSession.server)
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

    private fun autoOAuthLogin(server: String, email: String, accountId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val normalizedServer = server.trim().trimEnd('/')
                val tokenStore = TokenStore(application)
                val tokens = tokenStore.getTokens(normalizedServer, email)
                
                if (tokens == null) {
                    Log.w("LoginViewModel", "OAuth токены не найдены для автовхода")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }
                
                Log.d("LoginViewModel", "Найдены OAuth токены, попытка автовхода: expired=${tokens.isExpired()}, has_refresh=${tokens.refreshToken != null}")
                
                val httpClient = OAuthDiscovery.createClient()
                val discovery = OAuthDiscovery(httpClient)
                val discoveryUrl = "$normalizedServer/.well-known/oauth-authorization-server"
                val metadata = discovery.discover(discoveryUrl)
                
                val jmapClient = JmapOAuthClient.getOrCreate(
                    baseUrl = normalizedServer,
                    email = email,
                    accountId = accountId,
                    tokenStore = tokenStore,
                    metadata = metadata,
                    clientId = CLIENT_ID
                )
                
                val repository = MailRepository(jmapClient)
                repository.getAccount().fold(
                    onError = { e ->
                        Log.w("LoginViewModel", "OAuth автовход не удался", e)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = ErrorMapper.mapException(e)
                        )
                        if (e is com.mobilemail.data.jmap.OAuthTokenExpiredException) {
                            preferencesManager.clearSession()
                            tokenStore.clearTokens(normalizedServer, email)
                        }
                    },
                    onSuccess = { account ->
                        Log.d("LoginViewModel", "OAuth автовход успешен, account: ${account.id}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            account = account
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Ошибка OAuth автовхода", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorMapper.mapException(e)
                )
                if (e is com.mobilemail.data.jmap.OAuthTokenExpiredException) {
                    preferencesManager.clearSession()
                    tokenStore.clearTokens(normalizedServer, email)
                }
            }
        }
    }

    fun updateServer(server: String) {
        _uiState.value = _uiState.value.copy(server = server)
    }

    fun updateTotpCode(code: String) {
        _uiState.value = _uiState.value.copy(totpCode = code)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun startOAuthLogin(onSuccess: (Account) -> Unit) {
        val state = _uiState.value
        
        if (state.server.isBlank()) {
            _uiState.value = state.copy(error = AppError.UnknownError("Введите адрес сервера"))
            return
        }
        
        _uiState.value = state.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val normalizedServer = state.server.trim().trimEnd('/')
                
                val httpClient = OAuthDiscovery.createClient()
                discovery = OAuthDiscovery(httpClient)
                
                val discoveryUrl = "$normalizedServer/.well-known/oauth-authorization-server"
                val metadata = discovery!!.discover(discoveryUrl)
                
                deviceFlowClient = DeviceFlowClient(
                    metadata = metadata,
                    clientId = CLIENT_ID,
                    client = DeviceFlowClient.createClient()
                )
                
                val deviceCode = deviceFlowClient!!.requestDeviceCode(
                    scopes = listOf(
                        "urn:ietf:params:jmap:core",
                        "urn:ietf:params:jmap:mail",
                        "offline_access"
                    )
                )
                
                val expiresAt = System.currentTimeMillis() + (deviceCode.expiresIn * 1000L)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    oauthUserCode = deviceCode.userCode,
                    oauthVerificationUri = deviceCode.verificationUri,
                    oauthVerificationUriComplete = deviceCode.verificationUriComplete,
                    oauthExpiresAt = expiresAt
                )
                
                deviceFlowClient!!.pollForToken(
                    deviceCode = deviceCode.deviceCode,
                    interval = deviceCode.interval,
                    expiresAt = expiresAt
                ) { flowState ->
                    when (flowState) {
                        is DeviceFlowState.Success -> {
                            viewModelScope.launch {
                                handleOAuthTokenSuccess(
                                    server = normalizedServer,
                                    metadata = metadata,
                                    tokenResponse = flowState.tokenResponse,
                                    onSuccess = onSuccess
                                )
                            }
                        }
                        is DeviceFlowState.Error -> {
                            viewModelScope.launch {
                                handleOAuthTokenError(flowState.error)
                            }
                        }
                        is DeviceFlowState.WaitingForUser -> {
                            _uiState.value = _uiState.value.copy(
                                oauthExpiresAt = flowState.expiresAt
                            )
                        }
                        else -> {}
                    }
                }
            } catch (e: OAuthException) {
                Log.e("LoginViewModel", "Ошибка OAuth", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = AppError.NetworkError(
                        errorMessage = "Ошибка OAuth: ${e.message}",
                        errorCause = e,
                        isConnectionError = e.statusCode == null
                    )
                )
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Ошибка OAuth авторизации", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorMapper.mapException(e)
                )
            }
        }
    }
    
    private suspend fun handleOAuthTokenSuccess(
        server: String,
        metadata: com.mobilemail.data.oauth.OAuthServerMetadata,
        tokenResponse: com.mobilemail.data.oauth.TokenResponse,
        onSuccess: (Account) -> Unit
    ) {
        try {
            val email = server
            Log.d("LoginViewModel", "Сохранение OAuth токенов: expires_in=${tokenResponse.expiresIn}s, has_refresh=${tokenResponse.refreshToken != null}")
            tokenStore.saveTokens(server, email, tokenResponse)
            
            val savedTokens = tokenStore.getTokens(server, email)
            if (savedTokens == null) {
                throw Exception("Не удалось сохранить OAuth токены")
            }
            Log.d("LoginViewModel", "OAuth токены сохранены успешно: has_refresh=${savedTokens.refreshToken != null}")
            
            val jmapClient = JmapOAuthClient.getOrCreate(
                baseUrl = server,
                email = email,
                accountId = email,
                tokenStore = tokenStore,
                metadata = metadata,
                clientId = CLIENT_ID
            )
            
            val repository = MailRepository(jmapClient)
            repository.getAccount().fold(
                onError = { e ->
                    Log.w("LoginViewModel", "Не удалось получить аккаунт через OAuth", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorMapper.mapException(e)
                    )
                },
                onSuccess = { account ->
                    Log.d("LoginViewModel", "OAuth вход успешен, account: ${account.id}, сохранение сессии")
                    preferencesManager.saveSession(server, email, account.id)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        account = account,
                        oauthUserCode = null,
                        oauthVerificationUri = null,
                        oauthVerificationUriComplete = null,
                        oauthExpiresAt = null
                    )
                    onSuccess(account)
                }
            )
        } catch (e: Exception) {
            Log.e("LoginViewModel", "Ошибка после получения OAuth токена", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = ErrorMapper.mapException(e)
            )
        }
    }
    
    private suspend fun handleOAuthTokenError(error: OAuthDeviceFlowError) {
        val appError = when (error) {
            is OAuthDeviceFlowError.AuthorizationPending -> {
                return
            }
            is OAuthDeviceFlowError.SlowDown -> {
                return
            }
            is OAuthDeviceFlowError.ExpiredToken -> {
                AppError.AuthError("Время ожидания истекло. Начните авторизацию заново.")
            }
            is OAuthDeviceFlowError.AccessDenied -> {
                AppError.AuthError("Авторизация отклонена пользователем.")
            }
            is OAuthDeviceFlowError.NetworkError -> {
                AppError.NetworkError(
                    errorMessage = error.message,
                    errorCause = error.cause,
                    isConnectionError = true
                )
            }
            is OAuthDeviceFlowError.ServerError -> {
                AppError.ServerError(
                    errorMessage = error.message,
                    statusCode = error.statusCode
                )
            }
            is OAuthDeviceFlowError.UnknownError -> {
                AppError.UnknownError(error.message, error.cause)
            }
        }
        
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = appError,
            oauthUserCode = null,
            oauthVerificationUri = null,
            oauthVerificationUriComplete = null,
            oauthExpiresAt = null
        )
    }
    
    fun cancelOAuthLogin() {
        deviceFlowClient?.cancel()
        _uiState.value = _uiState.value.copy(
            oauthUserCode = null,
            oauthVerificationUri = null,
            oauthVerificationUriComplete = null,
            oauthExpiresAt = null
        )
    }
}
