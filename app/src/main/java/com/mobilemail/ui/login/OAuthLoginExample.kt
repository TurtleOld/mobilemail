package com.mobilemail.ui.login

import android.app.Application
import android.content.Intent
import android.net.Uri
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
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OAuthLoginUiState(
    val server: String = "",
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val userCode: String? = null,
    val verificationUri: String? = null,
    val verificationUriComplete: String? = null,
    val expiresAt: Long? = null,
    val account: Account? = null
)

class OAuthLoginViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)
    private val tokenStore = TokenStore(application)
    private val _uiState = MutableStateFlow(OAuthLoginUiState())
    val uiState: StateFlow<OAuthLoginUiState> = _uiState
    
    private var deviceFlowClient: DeviceFlowClient? = null
    private var discovery: OAuthDiscovery? = null
    
    companion object {
        private const val CLIENT_ID = "mail-client"
    }
    
    fun updateServer(server: String) {
        _uiState.value = _uiState.value.copy(server = server)
    }
    
    fun startOAuthLogin() {
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
                    userCode = deviceCode.userCode,
                    verificationUri = deviceCode.verificationUri,
                    verificationUriComplete = deviceCode.verificationUriComplete,
                    expiresAt = expiresAt
                )
                
                deviceFlowClient!!.pollForToken(
                    deviceCode = deviceCode.deviceCode,
                    interval = deviceCode.interval,
                    expiresAt = expiresAt
                ) { flowState ->
                    when (flowState) {
                        is DeviceFlowState.Success -> {
                            viewModelScope.launch {
                                handleTokenSuccess(
                                    server = normalizedServer,
                                    metadata = metadata,
                                    tokenResponse = flowState.tokenResponse
                                )
                            }
                        }
                        is DeviceFlowState.Error -> {
                            viewModelScope.launch {
                                handleTokenError(flowState.error)
                            }
                        }
                        is DeviceFlowState.WaitingForUser -> {
                            _uiState.value = _uiState.value.copy(
                                expiresAt = flowState.expiresAt
                            )
                        }
                        else -> {}
                    }
                }
            } catch (e: OAuthException) {
                Log.e("OAuthLoginViewModel", "Ошибка OAuth", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = AppError.NetworkError(
                        errorMessage = "Ошибка OAuth: ${e.message}",
                        errorCause = e,
                        isConnectionError = e.statusCode == null
                    )
                )
            } catch (e: Exception) {
                Log.e("OAuthLoginViewModel", "Ошибка авторизации", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorMapper.mapException(e)
                )
            }
        }
    }
    
    private suspend fun handleTokenSuccess(
        server: String,
        metadata: com.mobilemail.data.oauth.OAuthServerMetadata,
        tokenResponse: com.mobilemail.data.oauth.TokenResponse
    ) {
        try {
            tokenStore.saveTokens(server, server, tokenResponse)
            
            val jmapClient = JmapOAuthClient.getOrCreate(
                baseUrl = server,
                email = server,
                accountId = server,
                tokenStore = tokenStore,
                metadata = metadata,
                clientId = CLIENT_ID
            )
            
            val repository = MailRepository(jmapClient)
            repository.getAccount().fold(
                onError = { e ->
                    Log.w("OAuthLoginViewModel", "Не удалось получить аккаунт", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorMapper.mapException(e)
                    )
                },
                onSuccess = { account ->
                    Log.d("OAuthLoginViewModel", "Вход успешен, account: ${account.id}")
                    preferencesManager.saveSession(server, server, account.id)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        account = account,
                        userCode = null,
                        verificationUri = null,
                        verificationUriComplete = null,
                        expiresAt = null
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("OAuthLoginViewModel", "Ошибка после получения токена", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = ErrorMapper.mapException(e)
            )
        }
    }
    
    private suspend fun handleTokenError(error: OAuthDeviceFlowError) {
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
            userCode = null,
            verificationUri = null,
            verificationUriComplete = null,
            expiresAt = null
        )
    }
    
    fun openVerificationUri() {
        val state = _uiState.value
        val uri = state.verificationUriComplete ?: state.verificationUri
        if (uri != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        }
    }
    
    fun cancelLogin() {
        deviceFlowClient?.cancel()
        _uiState.value = OAuthLoginUiState(server = _uiState.value.server)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
