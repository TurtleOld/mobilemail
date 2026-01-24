# JmapClient API

Документация по использованию JMAP клиента.

## JmapOAuthClient

Основной клиент для работы с JMAP через OAuth авторизацию.

### Создание клиента

```kotlin
val jmapClient = JmapOAuthClient.getOrCreate(
    baseUrl = "https://mail.pavlovteam.ru",
    email = "user@example.com",
    accountId = "user@example.com",
    tokenStore = tokenStore,
    metadata = metadata,
    clientId = clientId
)
```

### Получение сессии

```kotlin
val session = jmapClient.getSession()
```

### Работа с почтовыми ящиками

```kotlin
val mailboxes = jmapClient.getMailboxes()
val mailboxesForAccount = jmapClient.getMailboxes(accountId = "account-id")
```

### Запрос писем

```kotlin
val queryResult = jmapClient.queryEmails(
    mailboxId = "inbox",
    position = 0,
    limit = 50
)
```

### Получение писем

```kotlin
val emails = jmapClient.getEmails(
    ids = queryResult.ids,
    properties = listOf("subject", "from", "preview")
)
```

### Обновление ключевых слов

```kotlin
val success = jmapClient.updateEmailKeywords(
    emailId = "email-id",
    keywords = mapOf("$seen" to true)
)
```

### Удаление письма

```kotlin
val success = jmapClient.deleteEmail(emailId = "email-id")
```

### Перемещение письма

```kotlin
val success = jmapClient.moveEmail(
    emailId = "email-id",
    fromMailboxId = "inbox",
    toMailboxId = "archive"
)
```

### Загрузка вложения

```kotlin
val attachmentData = jmapClient.downloadAttachment(
    blobId = "blob-id",
    accountId = "account-id"
)
```

## Автоматическое обновление токенов

JmapOAuthClient автоматически обновляет токены при получении 401/403 ошибок. Если refresh token недоступен, выбрасывается `OAuthTokenExpiredException`.

## Обработка ошибок

```kotlin
try {
    val session = jmapClient.getSession()
} catch (e: OAuthTokenExpiredException) {
    // Требуется повторная авторизация
    startOAuthFlow()
} catch (e: Exception) {
    // Другие ошибки
    handleError(e)
}
```
