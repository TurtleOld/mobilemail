# Конфигурация

Настройка приложения MobileMail.

## OAuth настройки

### Client ID

Client ID должен быть получен от администратора почтового сервера и задан через
переменную окружения или локальный `.env` файл:

```bash
OAUTH_CLIENT_ID=your-client-id
```

Gradle передает значение в приложение через `BuildConfig.OAUTH_CLIENT_ID`.

### Scopes

По умолчанию приложение запрашивает следующие scopes:

- `urn:ietf:params:jmap:core` — базовые возможности JMAP
- `urn:ietf:params:jmap:mail` — работа с почтой
- `offline_access` — получение refresh token

## Настройки сервера

### Discovery URL

Приложение автоматически определяет OAuth endpoints через discovery:

```
https://your-server.com/.well-known/oauth-authorization-server
```

### Ручная настройка

Если discovery недоступен, можно настроить endpoints вручную в коде.

## Безопасность

### Хранение токенов

Токены автоматически хранятся в EncryptedSharedPreferences с использованием Android Keystore.

### Сетевая безопасность

Все запросы выполняются только через HTTPS. HTTP соединения блокируются.

## Дополнительно

- [OAuth авторизация](../authentication/oauth-device-flow.md)
- [Безопасность](../authentication/security.md)
