# Стиль кода

Руководство по стилю кода для MobileMail.

## Kotlin стиль

Следуем [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).

## Именование

- **Классы:** PascalCase (`JmapOAuthClient`)
- **Функции:** camelCase (`getSession()`)
- **Константы:** UPPER_SNAKE_CASE (`CLIENT_ID`)
- **Переменные:** camelCase (`accessToken`)

## Форматирование

- 4 пробела для отступов
- Максимальная длина строки: 120 символов
- Пустые строки между функциями

## Комментарии

- Используйте KDoc для публичных API
- Комментируйте сложную логику
- Избегайте очевидных комментариев

## Обработка ошибок

- Используйте sealed classes для ошибок
- Всегда обрабатывайте исключения
- Логируйте ошибки без чувствительных данных

## Пример

```kotlin
/**
 * Получает сессию JMAP с автоматическим обновлением токена.
 * 
 * @return JmapSession сессия JMAP
 * @throws OAuthTokenExpiredException если токен истёк и не может быть обновлён
 */
suspend fun getSession(): JmapSession {
    // Реализация
}
```
