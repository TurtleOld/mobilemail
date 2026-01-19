# OAuth 2.0 Device Authorization Grant (RFC 8628)

Реализация авторизации клиента к Stalwart Mail Server через OAuth 2.0 Device Authorization Grant для доступа к JMAP.

## Обзор

Device Authorization Grant позволяет авторизовать устройства, которые не могут безопасно вводить учетные данные пользователя напрямую. Вместо этого устройство получает код, который пользователь вводит на другом устройстве (например, в браузере).

## Архитектура

### Компоненты системы

#### 1. OAuthDiscovery

**Класс:** `com.mobilemail.data.oauth.OAuthDiscovery`

Получает метаданные OAuth сервера из discovery endpoint.

**Основные функции:**
- Извлекает `device_authorization_endpoint`, `token_endpoint`, `issuer`
- Определяет поддерживаемые grant types и scopes
- Использует HTTPS и корректную обработку ошибок

**Пример использования:**

```kotlin
val discovery = OAuthDiscovery(OAuthDiscovery.createClient())
val metadata = discovery.discover("https://mail.pavlovteam.ru")
```

#### 2. DeviceFlowClient

**Класс:** `com.mobilemail.data.oauth.DeviceFlowClient`

Реализует OAuth 2.0 Device Authorization Grant flow.

**Основные функции:**
- Запрашивает device code
- Выполняет polling для получения access token
- Обрабатывает ошибки: `authorization_pending`, `slow_down`, `expired_token`, `access_denied`

**Пример использования:**

```kotlin
val deviceFlowClient = DeviceFlowClient(
    metadata = metadata,
    clientId = "YOUR_CLIENT_ID",
    client = DeviceFlowClient.createClient()
)
```

#### 3. TokenStore

**Класс:** `com.mobilemail.data.oauth.TokenStore`

Безопасное хранение OAuth токенов.

**Основные функции:**
- Хранение в EncryptedSharedPreferences (Android Keystore)
- Управление access token и refresh token
- Проверка истечения токенов

#### 4. OAuthTokenRefresh

**Класс:** `com.mobilemail.data.oauth.OAuthTokenRefresh`

Обновление access token через refresh token.

**Основные функции:**
- Автоматическое обновление при истечении
- Прозрачная работа с refresh token

#### 5. JmapOAuthClient

**Класс:** `com.mobilemail.data.jmap.JmapOAuthClient`

JMAP клиент с поддержкой OAuth Bearer токенов.

**Основные функции:**
- Автоматическое обновление токенов при 401/403
- Полная совместимость с существующим JmapClient API

## Процесс авторизации

### Шаг 1: Discovery

Клиент получает метаданные OAuth сервера.

**HTTP запрос:**

```bash
curl -X GET \
  'https://mail.pavlovteam.ru/.well-known/oauth-authorization-server' \
  -H 'Accept: application/json'
```

**Ответ:**

```json
{
  "issuer": "https://mail.pavlovteam.ru",
  "device_authorization_endpoint": "https://mail.pavlovteam.ru/auth/device",
  "token_endpoint": "https://mail.pavlovteam.ru/auth/token",
  "authorization_endpoint": "https://mail.pavlovteam.ru/auth/authorize",
  "grant_types_supported": [
    "urn:ietf:params:oauth:grant-type:device_code",
    "refresh_token"
  ],
  "scopes_supported": [
    "urn:ietf:params:jmap:core",
    "urn:ietf:params:jmap:mail",
    "offline_access"
  ]
}
```

### Шаг 2: Получение Device Code

Клиент запрашивает device code и user code для пользователя.

**HTTP запрос:**

```bash
curl -X POST \
  'https://mail.pavlovteam.ru/auth/device' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=YOUR_CLIENT_ID&scope=urn:ietf:params:jmap:core urn:ietf:params:jmap:mail offline_access'
```

**Ответ:**

```json
{
  "device_code": "DEVICE_CODE_STRING",
  "user_code": "ABCD-1234",
  "verification_uri": "https://mail.pavlovteam.ru/auth/verify",
  "verification_uri_complete": "https://mail.pavlovteam.ru/auth/verify?user_code=ABCD-1234",
  "expires_in": 600,
  "interval": 5
}
```

### Шаг 3: Polling для получения токена

Клиент периодически опрашивает token endpoint до получения токена.

**HTTP запрос:**

```bash
curl -X POST \
  'https://mail.pavlovteam.ru/auth/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code=DEVICE_CODE_STRING&client_id=YOUR_CLIENT_ID'
```

**Успешный ответ:**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "REFRESH_TOKEN_STRING"
}
```

**Возможные ошибки:**

| Ошибка | Описание | Действие |
|--------|----------|----------|
| `authorization_pending` | Пользователь ещё не авторизовал устройство | Продолжить polling |
| `slow_down` | Слишком частые запросы | Увеличить интервал на 5 секунд |
| `expired_token` | Device code истёк | Остановить polling, начать заново |
| `access_denied` | Пользователь отклонил авторизацию | Остановить polling |

### Шаг 4: Использование токена для JMAP

После получения токена клиент может использовать его для доступа к JMAP API.

**HTTP запрос:**

```bash
curl -X GET \
  'https://mail.pavlovteam.ru/.well-known/jmap' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...'
```

**Ответ:**

```json
{
  "apiUrl": "https://mail.pavlovteam.ru/jmap",
  "downloadUrl": "https://mail.pavlovteam.ru/jmap/download/{accountId}/{blobId}/attachment",
  "uploadUrl": "https://mail.pavlovteam.ru/jmap/upload/{accountId}",
  "eventSourceUrl": "https://mail.pavlovteam.ru/jmap/events",
  "accounts": {
    "account-id": {
      "name": "user@example.com",
      "isPersonal": true,
      "isReadOnly": false
    }
  },
  "primaryAccounts": {
    "mail": "account-id"
  }
}
```

### Шаг 5: Обновление токена (Refresh Token)

При истечении access token клиент может обновить его через refresh token.

**HTTP запрос:**

```bash
curl -X POST \
  'https://mail.pavlovteam.ru/auth/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=refresh_token&refresh_token=REFRESH_TOKEN_STRING&client_id=YOUR_CLIENT_ID'
```

**Ответ:**

```json
{
  "access_token": "NEW_ACCESS_TOKEN",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "NEW_REFRESH_TOKEN"
}
```

## Примеры кода

### Полный пример инициализации

```kotlin
val discovery = OAuthDiscovery(OAuthDiscovery.createClient())
val metadata = discovery.discover("https://mail.pavlovteam.ru")

val deviceFlowClient = DeviceFlowClient(
    metadata = metadata,
    clientId = "YOUR_CLIENT_ID",
    client = DeviceFlowClient.createClient()
)

val deviceCode = deviceFlowClient.requestDeviceCode(
    scopes = listOf(
        "urn:ietf:params:jmap:core",
        "urn:ietf:params:jmap:mail",
        "offline_access"
    )
)

// Показать пользователю userCode и verificationUri
showUserCode(
    deviceCode.userCode, 
    deviceCode.verificationUriComplete ?: deviceCode.verificationUri
)

// Начать polling
val expiresAt = System.currentTimeMillis() + (deviceCode.expiresIn * 1000L)
deviceFlowClient.pollForToken(
    deviceCode = deviceCode.deviceCode,
    interval = deviceCode.interval,
    expiresAt = expiresAt
) { state ->
    when (state) {
        is DeviceFlowState.Success -> {
            // Сохранить токены
            tokenStore.saveTokens(server, email, state.tokenResponse)
            // Создать JmapOAuthClient
            val jmapClient = JmapOAuthClient.getOrCreate(
                baseUrl = server,
                email = email,
                accountId = email,
                tokenStore = tokenStore,
                metadata = metadata,
                clientId = clientId
            )
        }
        is DeviceFlowState.Error -> {
            // Обработать ошибку
            handleError(state.error)
        }
        is DeviceFlowState.WaitingForUser -> {
            // Обновить UI с таймером
            updateTimer(state.expiresAt)
        }
        else -> {}
    }
}
```

### Использование JmapOAuthClient

```kotlin
val jmapClient = JmapOAuthClient.getOrCreate(
    baseUrl = "https://mail.pavlovteam.ru",
    email = "user@example.com",
    accountId = "user@example.com",
    tokenStore = tokenStore,
    metadata = metadata,
    clientId = clientId
)

// Использование как обычный JmapClient
val session = jmapClient.getSession()
val mailboxes = jmapClient.getMailboxes()
val emails = jmapClient.queryEmails(mailboxId = "inbox")
```

## Обработка ошибок

### Типовые ошибки

#### NetworkError

**Причина:** Проблемы с сетью, таймауты, DNS ошибки

**Действие:** Показать пользователю сообщение о проблемах с сетью, предложить повторить

#### OAuthException (Discovery failed)

**Причина:** Сервер недоступен, неправильный discovery URL

**Действие:** Проверить адрес сервера, показать ошибку подключения

#### AuthorizationPending

**Причина:** Пользователь ещё не авторизовал устройство

**Действие:** Продолжить polling, показать инструкции пользователю

#### SlowDown

**Причина:** Слишком частые запросы polling

**Действие:** Увеличить интервал polling на 5 секунд

#### ExpiredToken

**Причина:** Device code истёк (обычно через 10 минут)

**Действие:** Остановить polling, показать ошибку, предложить начать заново

#### AccessDenied

**Причина:** Пользователь отклонил авторизацию

**Действие:** Остановить polling, показать сообщение об отклонении

#### OAuthTokenExpiredException

**Причина:** Access token истёк и refresh token не помог

**Действие:** Очистить токены, начать Device Flow заново

#### ServerError (401/403)

**Причина:** Неверный или истёкший токен

**Действие:** Попытаться обновить через refresh token, если не помогло - начать Device Flow заново

## Безопасность

### Хранение токенов

1. **EncryptedSharedPreferences** — токены хранятся в зашифрованном виде с использованием Android Keystore
2. **Токены не логируются** — все логи содержат только метаданные
3. **Строго HTTPS** — все запросы идут только через TLS
4. **Автоматическое обновление токенов** — refresh token используется прозрачно
5. **Отмена процесса** — можно отменить polling в любой момент

### Логирование

Все классы логируют только метаданные:

- URLs endpoints
- Статусы запросов
- Ошибки (без токенов)
- Временные метки

**Важно:** Токены никогда не попадают в логи.

## Дополнительные ресурсы

- [RFC 8628 - OAuth 2.0 Device Authorization Grant](https://tools.ietf.org/html/rfc8628)
- [JMAP Specification](https://jmap.io/)
- [Примеры использования](examples/oauth-login.md)
