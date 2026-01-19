# Руководство по написанию KDoc комментариев

KDoc — это система документации для Kotlin, аналогичная JavaDoc. Dokka генерирует документацию из KDoc комментариев в вашем коде.

## Базовый синтаксис

```kotlin
/**
 * Краткое описание функции или класса.
 * 
 * Подробное описание, которое может занимать несколько строк.
 * 
 * @param paramName Описание параметра
 * @return Описание возвращаемого значения
 * @throws ExceptionType Когда выбрасывается исключение
 */
```

## Примеры

### Класс

```kotlin
/**
 * OAuth 2.0 Device Authorization Grant клиент.
 * 
 * Реализует процесс авторизации устройства через OAuth 2.0 Device Flow (RFC 8628).
 * Клиент запрашивает device code, выполняет polling для получения access token
 * и обрабатывает все возможные ошибки процесса авторизации.
 * 
 * @param metadata Метаданные OAuth сервера, полученные из discovery endpoint
 * @param clientId Идентификатор OAuth клиента
 * @param client HTTP клиент для выполнения запросов
 * 
 * @see [OAuthDiscovery] для получения метаданных сервера
 * @see [TokenStore] для хранения полученных токенов
 * 
 * @sample com.mobilemail.data.oauth.DeviceFlowClientExample.usage
 */
class DeviceFlowClient(
    private val metadata: OAuthServerMetadata,
    private val clientId: String,
    private val client: OkHttpClient
)
```

### Функция

```kotlin
/**
 * Запрашивает device code для авторизации устройства.
 *
 * Отправляет POST запрос на device_authorization_endpoint с указанными scopes.
 * В ответе получает device_code, user_code и verification_uri.
 *
 * @param scopes Список OAuth scopes для запроса. По умолчанию включает:
 *   - `urn:ietf:params:jmap:core` — базовые возможности JMAP
 *   - `urn:ietf:params:jmap:mail` — работа с почтой
 *   - `offline_access` — получение refresh token
 * @return Ответ с device code, user code, verification URI и временными параметрами
 * @throws OAuthException если запрос не удался или ответ невалиден
 * 
 * @sample com.mobilemail.data.oauth.DeviceFlowClientExample.requestDeviceCode
 */
suspend fun requestDeviceCode(
    scopes: List<String> = listOf(
        "urn:ietf:params:jmap:core",
        "urn:ietf:params:jmap:mail",
        "offline_access"
    )
): DeviceCodeResponse
```

### Свойство

```kotlin
/**
 * Access token для авторизации запросов к JMAP API.
 * 
 * Токен автоматически обновляется при истечении через refresh token.
 * Хранится в EncryptedSharedPreferences для безопасности.
 * 
 * @see [refreshToken] для обновления токена
 * @see [TokenStore] для управления токенами
 */
val accessToken: String
```

### Data класс

```kotlin
/**
 * Ответ на запрос device code.
 *
 * @param deviceCode Код устройства для polling
 * @param userCode Код для отображения пользователю (например, "ABCD-1234")
 * @param verificationUri URL для авторизации устройства
 * @param verificationUriComplete Полный URL с user_code (если поддерживается)
 * @param expiresIn Время жизни device code в секундах
 * @param interval Рекомендуемый интервал polling в секундах
 */
data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val expiresIn: Int,
    val interval: Int
)
```

## Теги KDoc

### @param
Описание параметра функции или конструктора.

```kotlin
/**
 * @param email Email адрес пользователя
 * @param password Пароль (не используется в OAuth flow)
 */
```

### @return
Описание возвращаемого значения.

```kotlin
/**
 * @return Метаданные OAuth сервера
 */
```

### @throws
Описание исключений, которые может выбросить функция.

```kotlin
/**
 * @throws OAuthException если discovery запрос не удался
 * @throws IOException если произошла сетевая ошибка
 */
```

### @see
Ссылка на связанную документацию.

```kotlin
/**
 * @see [OAuthDiscovery] для получения метаданных
 * @see [TokenStore.saveTokens] для сохранения токенов
 */
```

### @sample
Ссылка на пример кода.

```kotlin
/**
 * @sample com.mobilemail.data.oauth.DeviceFlowClientExample.usage
 */
```

### @since
Версия, с которой доступен API.

```kotlin
/**
 * @since 1.0.0
 */
```

### @deprecated
Устаревший API.

```kotlin
/**
 * @deprecated Используйте [newMethod] вместо этого
 */
@Deprecated("Используйте newMethod")
fun oldMethod() { }
```

## Markdown в KDoc

KDoc поддерживает Markdown синтаксис:

```kotlin
/**
 * Используйте **жирный текст** и *курсив*.
 * 
 * - Список элементов
 * - Еще один элемент
 * 
 * `код` в тексте
 * 
 * ```kotlin
 * val example = "код блок"
 * ```
 */
```

## Лучшие практики

1. **Всегда документируйте публичные API** — классы, функции, свойства
2. **Начинайте с краткого описания** — первое предложение должно быть кратким
3. **Используйте теги** — `@param`, `@return`, `@throws` для ясности
4. **Добавляйте примеры** — используйте `@sample` для сложных API
5. **Обновляйте документацию** — при изменении API обновляйте KDoc
6. **Избегайте очевидного** — не документируйте то, что понятно из названия

## Генерация документации

После добавления KDoc комментариев:

```bash
./gradlew dokkaHtml
```

Документация будет сгенерирована в `docs/api/`.
