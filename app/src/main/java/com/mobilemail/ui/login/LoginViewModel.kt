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
    private val app = getApplication<Application>()
    private val preferencesManager = PreferencesManager(app)
    private val tokenStore = TokenStore(app)
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
                val tokens = tokenStore.getTokens(server, email)

                if (tokens == null) {
                    Log.w("LoginViewModel", "OAuth токены не найдены для автовхода")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                Log.d("LoginViewModel", "Найдены OAuth токены, попытка автовхода: expired=${tokens.isExpired()}, has_refresh=${tokens.refreshToken != null}")

                val normalizedServer = server.let { url ->
                    if (url.startsWith("http")) url else "https://$url"
                }
                Log.d("LoginViewModel", "Нормализованный URL сервера для автовхода: $normalizedServer")

                val httpClient = OAuthDiscovery.createClient()
                val discovery = OAuthDiscovery(httpClient)
                val metadata = preferencesManager.getOAuthMetadata(normalizedServer) ?: run {
                    val discovered = discovery.discover(normalizedServer)
                    preferencesManager.saveOAuthMetadata(normalizedServer, discovered)
                    Log.d("LoginViewModel", "OAuth metadata discovered:")
                    Log.d("LoginViewModel", "  Issuer: ${discovered.issuer}")
                    Log.d("LoginViewModel", "  Device Authorization Endpoint: ${discovered.deviceAuthorizationEndpoint}")
                    Log.d("LoginViewModel", "  Token Endpoint: ${discovered.tokenEndpoint}")
                    Log.d("LoginViewModel", "  Authorization Endpoint: ${discovered.authorizationEndpoint}")
                    Log.d("LoginViewModel", "  Grant Types: ${discovered.grantTypesSupported.joinToString(", ")}")
                    discovered
                }
                
                Log.d("LoginViewModel", "Using OAuth metadata:")
                Log.d("LoginViewModel", "  Issuer: ${metadata.issuer}")
                Log.d("LoginViewModel", "  Device Authorization Endpoint: ${metadata.deviceAuthorizationEndpoint}")
                Log.d("LoginViewModel", "  Token Endpoint: ${metadata.tokenEndpoint}")

                val jmapClient = JmapOAuthClient.getOrCreate(
                    serverUrl = server,
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
                            tokenStore.clearTokens(server, email)
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
                    tokenStore.clearTokens(server, email)
                }
            }
        }
    }

    fun updateServer(server: String) {
        _uiState.value = _uiState.value.copy(server = server)
        viewModelScope.launch {
            val trimmed = server.trim()
            if (trimmed.isNotEmpty()) {
                preferencesManager.saveServerUrl(trimmed)
                Log.d("LoginViewModel", "Сохранен адрес сервера: $trimmed")
            }
        }
    }

    fun updateTotpCode(code: String) {
        _uiState.value = _uiState.value.copy(totpCode = code)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun startOAuthLogin(onSuccess: (Account) -> Unit) {
        val state = _uiState.value

        Log.d("LoginViewModel", "=== STARTING OAuth LOGIN PROCESS ===")
        Log.d("LoginViewModel", "Current server: '${state.server}'")

        if (state.server.isBlank()) {
            Log.w("LoginViewModel", "Server is blank, showing error")
            _uiState.value = state.copy(error = AppError.UnknownError("Введите адрес сервера"))
            return
        }

        Log.d("LoginViewModel", "Server validation passed, starting OAuth login")
        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val serverUrl = state.server.trim().let { url ->
                    if (url.startsWith("http")) url else "https://$url"
                }
                Log.d("LoginViewModel", "Нормализованный URL сервера: $serverUrl")
                Log.d("LoginViewModel", "Creating HTTP client and discovery service")

                val httpClient = OAuthDiscovery.createClient()
                Log.d("LoginViewModel", "HTTP client created")
                discovery = OAuthDiscovery(httpClient)
                Log.d("LoginViewModel", "Discovery service created")

                Log.d("LoginViewModel", "Checking for cached OAuth metadata")
                val metadata = preferencesManager.getOAuthMetadata(serverUrl) ?: run {
                    Log.d("LoginViewModel", "No cached metadata found, starting discovery")
                    val discovered = discovery?.discover(serverUrl)
                        ?: throw IllegalStateException("OAuth discovery service not initialized")
                    preferencesManager.saveOAuthMetadata(serverUrl, discovered)
                    Log.d("LoginViewModel", "Discovery completed successfully")
                    discovered
                }
                Log.d("LoginViewModel", "Using metadata: ${metadata.issuer}")
                Log.d("LoginViewModel", "Device Auth Endpoint: ${metadata.deviceAuthorizationEndpoint}")
                Log.d("LoginViewModel", "Token Endpoint: ${metadata.tokenEndpoint}")
                Log.d("LoginViewModel", "Grant Types: ${metadata.grantTypesSupported.joinToString(", ")}")

                // Validate metadata before using
                if (metadata.deviceAuthorizationEndpoint.isBlank() || metadata.tokenEndpoint.isBlank()) {
                    Log.e("LoginViewModel", "OAuth metadata is incomplete - cannot proceed with OAuth flow")
                    Log.e("LoginViewModel", "Device auth endpoint: '${metadata.deviceAuthorizationEndpoint}'")
                    Log.e("LoginViewModel", "Token endpoint: '${metadata.tokenEndpoint}'")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = AppError.NetworkError(
                            errorMessage = "OAuth configuration error: Server metadata is incomplete",
                            isConnectionError = true
                        )
                    )
                    return@launch
                }

                deviceFlowClient = DeviceFlowClient(
                    metadata = metadata,
                    clientId = CLIENT_ID,
                    client = DeviceFlowClient.createClient()
                )

                Log.d("LoginViewModel", "Requesting device code via DeviceFlowClient")
                val deviceCode = deviceFlowClient!!.requestDeviceCode(
                    scopes = listOf(
                        "urn:ietf:params:jmap:core",
                        "urn:ietf:params:jmap:mail",
                        "offline_access"
                    )
                )
                Log.d("LoginViewModel", "Device code received: user_code=${deviceCode.userCode}, expires_in=${deviceCode.expiresIn}, interval=${deviceCode.interval}")

                val expiresAt = System.currentTimeMillis() + (deviceCode.expiresIn * 1000L)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    oauthUserCode = deviceCode.userCode,
                    oauthVerificationUri = deviceCode.verificationUri,
                    oauthVerificationUriComplete = deviceCode.verificationUriComplete,
                    oauthExpiresAt = expiresAt
                )

                Log.d("LoginViewModel", "Starting token polling")
                deviceFlowClient!!.pollForToken(
                    deviceCode = deviceCode.deviceCode,
                    interval = deviceCode.interval,
                    expiresAt = expiresAt
                ) { flowState ->
                    when (flowState) {
                        is DeviceFlowState.Success -> {
                            viewModelScope.launch {
                                handleOAuthTokenSuccess(
                                    server = serverUrl,
                                    metadata = metadata,
                                    tokenResponse = flowState.tokenResponse,
                                    onSuccess = onSuccess
                                )
                            }
                        }
                        is DeviceFlowState.Error -> {
                            viewModelScope.launch {
                                Log.e("LoginViewModel", "OAuth device flow error: ${flowState.error}")
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
            val tempEmail = server
            Log.d("LoginViewModel", "Сохранение OAuth токенов: expires_in=${tokenResponse.expiresIn}s, has_refresh=${tokenResponse.refreshToken != null}")
            tokenStore.saveTokens(server, tempEmail, tokenResponse)
            
            val savedTokens = tokenStore.getTokens(server, tempEmail)
            if (savedTokens == null) {
                throw Exception("Не удалось сохранить OAuth токены")
            }
            Log.d("LoginViewModel", "OAuth токены сохранены успешно: has_refresh=${savedTokens.refreshToken != null}")
            
            val jmapClient = JmapOAuthClient.getOrCreate(
                serverUrl = server,
                email = tempEmail,
                accountId = tempEmail,
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
                    val accountEmail = account.email.ifBlank { tempEmail }
                    if (accountEmail != tempEmail) {
                        tokenStore.saveTokens(server, accountEmail, tokenResponse)
                        tokenStore.clearTokens(server, tempEmail)
                    }
                    Log.d(
                        "LoginViewModel",
                        "Сохранение сессии: server=$server, accountEmail=$accountEmail, tempEmail=$tempEmail, accountId=${account.id}"
                    )
                    preferencesManager.saveSession(server, accountEmail, account.id)
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
