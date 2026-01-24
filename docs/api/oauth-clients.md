# OAuth клиенты API

Документация по использованию OAuth клиентов.

## OAuthDiscovery

Получение метаданных OAuth сервера.

### Методы

#### discover(discoveryUrl: String): OAuthServerMetadata

Получает метаданные OAuth сервера из discovery endpoint.

```kotlin
val discovery = OAuthDiscovery(OAuthDiscovery.createClient())
val metadata = discovery.discover("https://mail.pavlovteam.ru")
```

## DeviceFlowClient

Реализация OAuth 2.0 Device Authorization Grant flow.

### Методы

#### requestDeviceCode(scopes: List<String>): DeviceCodeResponse

Запрашивает device code для авторизации.

```kotlin
val deviceCode = deviceFlowClient.requestDeviceCode(
    scopes = listOf(
        "urn:ietf:params:jmap:core",
        "urn:ietf:params:jmap:mail",
        "offline_access"
    )
)
```

#### pollForToken(...)

Начинает polling для получения access token.

```kotlin
deviceFlowClient.pollForToken(
    deviceCode = deviceCode.deviceCode,
    interval = deviceCode.interval,
    expiresAt = expiresAt
) { state ->
    // Обработка состояний
}
```

#### cancel()

Отменяет процесс polling.

```kotlin
deviceFlowClient.cancel()
```

## TokenStore

Безопасное хранение OAuth токенов.

### Методы

#### saveTokens(server: String, email: String, tokenResponse: TokenResponse)

Сохраняет токены в безопасном хранилище.

```kotlin
tokenStore.saveTokens(server, email, tokenResponse)
```

#### getTokens(server: String, email: String): StoredToken?

Получает сохранённые токены.

```kotlin
val tokens = tokenStore.getTokens(server, email)
```

#### clearTokens(server: String, email: String)

Удаляет токены для указанного сервера и email.

```kotlin
tokenStore.clearTokens(server, email)
```

#### hasValidTokens(server: String, email: String): Boolean

Проверяет наличие валидных токенов.

```kotlin
if (tokenStore.hasValidTokens(server, email)) {
    // Токены валидны
}
```

## OAuthTokenRefresh

Обновление access token через refresh token.

### Методы

#### refreshToken(refreshToken: String): TokenResponse

Обновляет access token используя refresh token.

```kotlin
val tokenRefresh = OAuthTokenRefresh(metadata, clientId, client)
val newToken = tokenRefresh.refreshToken(refreshToken)
```
