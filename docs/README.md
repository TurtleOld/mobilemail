# Документация MobileMail

Документация проекта генерируется автоматически из KDoc комментариев в исходном коде с использованием [Dokka](https://kotlinlang.org/docs/dokka-introduction.html).

## Генерация документации

### Через Gradle

```bash
./gradlew dokkaHtml
```

Документация будет сгенерирована в папке `docs/api/`.

### Через Makefile

```bash
make docs-build
```

### Просмотр документации

После генерации откройте `docs/api/index.html` в браузере.

Для локального сервера:

```bash
make docs-serve
```

## KDoc комментарии

Документация генерируется из KDoc комментариев в коде. Примеры:

### Класс

```kotlin
/**
 * OAuth 2.0 Device Authorization Grant клиент.
 * 
 * Реализует процесс авторизации устройства через OAuth 2.0 Device Flow (RFC 8628).
 * 
 * @param metadata Метаданные OAuth сервера
 * @param clientId Идентификатор клиента
 * @param client HTTP клиент для выполнения запросов
 * 
 * @see [OAuthDiscovery] для получения метаданных сервера
 * @see [TokenStore] для хранения токенов
 */
class DeviceFlowClient(...)
```

### Функция

```kotlin
/**
 * Запрашивает device code для авторизации.
 *
 * @param scopes Список scopes для запроса (по умолчанию: core, mail, offline_access)
 * @return Ответ с device code, user code и verification URI
 * @throws OAuthException если запрос не удался
 */
suspend fun requestDeviceCode(scopes: List<String> = ...): DeviceCodeResponse
```

### Свойство

```kotlin
/**
 * Access token для авторизации запросов.
 * 
 * Токен автоматически обновляется при истечении через refresh token.
 * 
 * @see [refreshToken] для обновления токена
 */
val accessToken: String
```

## Теги KDoc

- `@param` — описание параметра
- `@return` — описание возвращаемого значения
- `@throws` — описание исключений
- `@see` — ссылка на связанную документацию
- `@sample` — ссылка на пример кода
- `@since` — версия, с которой доступно
- `@deprecated` — устаревший API

## Структура документации

- `docs/api/` — сгенерированная HTML документация (не коммитится)
- `docs/package.md` — описание модуля/пакета
- Исходный код с KDoc комментариями

## Настройка

Конфигурация Dokka находится в `app/build.gradle.kts`:

```kotlin
tasks.dokkaHtml {
    outputDirectory.set(file("${project.rootDir}/docs/api"))
    // ... настройки
}
```

## Лучшие практики

1. **Документируйте публичные API** — все публичные классы, функции и свойства
2. **Используйте теги** — `@param`, `@return`, `@throws`, `@see`
3. **Добавляйте примеры** — используйте `@sample` для ссылок на примеры
4. **Обновляйте документацию** — при изменении API обновляйте KDoc

## Дополнительная информация

- [Официальная документация Dokka](https://kotlinlang.org/docs/dokka-introduction.html)
- [KDoc синтаксис](https://kotlinlang.org/docs/kotlin-doc.html)
- [Примеры KDoc комментариев](https://kotlinlang.org/docs/kotlin-doc.html#block-tags)
