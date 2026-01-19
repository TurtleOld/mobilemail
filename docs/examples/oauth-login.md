# Пример OAuth авторизации

Полный пример реализации OAuth авторизации в Android приложении.

## ViewModel для OAuth авторизации

```kotlin
class OAuthLoginViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val _uiState = MutableStateFlow(OAuthLoginUiState())
    val uiState: StateFlow<OAuthLoginUiState> = _uiState
    
    private var deviceFlowClient: DeviceFlowClient? = null
    
    fun startOAuthLogin() {
        val state = _uiState.value
        
        if (state.server.isBlank()) {
            _uiState.value = state.copy(error = "Введите адрес сервера")
            return
        }
        
        _uiState.value = state.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val normalizedServer = state.server.trim().trimEnd('/')
                
                val httpClient = OAuthDiscovery.createClient()
                val discovery = OAuthDiscovery(httpClient)
                
                val discoveryUrl = "$normalizedServer/.well-known/oauth-authorization-server"
                val metadata = discovery.discover(discoveryUrl)
                
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    private suspend fun handleTokenSuccess(
        server: String,
        metadata: OAuthServerMetadata,
        tokenResponse: TokenResponse
    ) {
        tokenStore.saveTokens(server, server, tokenResponse)
        
        val jmapClient = JmapOAuthClient.getOrCreate(
            baseUrl = server,
            email = server,
            accountId = server,
            tokenStore = tokenStore,
            metadata = metadata,
            clientId = CLIENT_ID
        )
        
        // Продолжить работу с приложением
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
    }
}
```

## UI компонент

```kotlin
@Composable
fun OAuthLoginScreen(viewModel: OAuthLoginViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextField(
            value = uiState.server,
            onValueChange = viewModel::updateServer,
            label = { Text("Адрес сервера") }
        )
        
        if (uiState.userCode != null) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Введите код:", style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiState.userCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Button(onClick = viewModel::openVerificationUri) {
                        Text("Открыть в браузере")
                    }
                    
                    if (uiState.expiresAt != null) {
                        val remaining = (uiState.expiresAt - System.currentTimeMillis()) / 1000
                        Text("Осталось: ${remaining}с")
                    }
                }
            }
        }
        
        Button(
            onClick = { viewModel.startOAuthLogin() },
            enabled = !uiState.isLoading && uiState.server.isNotBlank()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Войти")
            }
        }
        
        if (uiState.error != null) {
            Text(
                uiState.error,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
```

## Обработка состояний

```kotlin
when (flowState) {
    is DeviceFlowState.Success -> {
        // Токен получен, сохранить и продолжить
        tokenStore.saveTokens(server, email, flowState.tokenResponse)
    }
    is DeviceFlowState.Error -> {
        when (flowState.error) {
            is OAuthDeviceFlowError.ExpiredToken -> {
                // Показать ошибку и предложить начать заново
            }
            is OAuthDeviceFlowError.AccessDenied -> {
                // Показать сообщение об отклонении
            }
            else -> {
                // Обработать другие ошибки
            }
        }
    }
    is DeviceFlowState.WaitingForUser -> {
        // Обновить таймер
        updateTimer(flowState.expiresAt)
    }
    else -> {}
}
```
