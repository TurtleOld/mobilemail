# Безопасность

Руководство по безопасности в MobileMail.

## Хранение токенов

### EncryptedSharedPreferences

Токены хранятся в EncryptedSharedPreferences с использованием Android Keystore:

- Шифрование на уровне AES-256
- Ключи хранятся в Android Keystore
- Защита от извлечения на скомпрометированных устройствах

### Управление токенами

```kotlin
// Сохранение
tokenStore.saveTokens(server, email, tokenResponse)

// Получение
val tokens = tokenStore.getTokens(server, email)

// Удаление
tokenStore.clearTokens(server, email)
```

## Сетевая безопасность

### HTTPS только

Все запросы выполняются только через HTTPS. HTTP соединения блокируются на уровне приложения.

### Certificate Pinning

Для дополнительной безопасности можно настроить certificate pinning в OkHttp клиенте.

## Логирование

### Что логируется

- URLs endpoints
- Статусы запросов
- Ошибки (без токенов)
- Временные метки

### Что НЕ логируется

- Access tokens
- Refresh tokens
- Device codes
- User codes (после использования)
- Пароли (не используются в OAuth flow)

## OAuth безопасность

### Client ID

Client ID не является секретом, но должен быть валидирован сервером.

### Scopes

Запрашивайте только необходимые scopes:

- `urn:ietf:params:jmap:core` — базовые возможности
- `urn:ietf:params:jmap:mail` — работа с почтой
- `offline_access` — только если нужен refresh token

### Token expiration

- Access tokens имеют ограниченное время жизни
- Refresh tokens используются для обновления
- При истечении refresh token требуется повторная авторизация

## Рекомендации

1. **Не храните пароли** — используйте только OAuth
2. **Регулярно обновляйте токены** — используйте refresh token
3. **Очищайте токены при выходе** — вызывайте `clearTokens()`
4. **Используйте HTTPS** — никогда не используйте HTTP
5. **Проверяйте сертификаты** — используйте certificate pinning в production
