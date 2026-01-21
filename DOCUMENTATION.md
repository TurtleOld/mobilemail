# Документация проекта

Проект использует [Dokka](https://kotlinlang.org/docs/dokka-introduction.html) — официальный инструмент от JetBrains для генерации документации из Kotlin кода.

Документация генерируется автоматически из KDoc комментариев в исходном коде и не требует дополнительных зависимостей.

## Быстрый старт

### Генерация документации

```bash
# Генерация HTML документации
./gradlew dokkaHtml

# Или через Makefile
make docs-build
```

Документация будет сгенерирована в папке `docs/api/`.

### Просмотр документации

После генерации откройте файл `docs/api/index.html` в браузере.

## KDoc комментарии

Документация генерируется из KDoc комментариев в коде. Пример:

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
 * 
 * @sample com.mobilemail.data.oauth.DeviceFlowClientExample.usage
 */
class DeviceFlowClient(
    private val metadata: OAuthServerMetadata,
    private val clientId: String,
    private val client: OkHttpClient
)
```

## Структура документации

- `docs/api/` — сгенерированная HTML документация
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

## Публикация документации

### GitHub Pages (автоматически)

Документация автоматически публикуется на GitHub Pages при пуше в ветку `main` через GitHub Actions (см. `.github/workflows/docs.yml`).

Документация будет доступна по адресу:
**https://turtleold.github.io/mobilemail/**

После первого запуска workflow документация будет доступна через несколько минут.

**Настройка GitHub Pages:**

1. Перейдите в Settings → Pages вашего репозитория
2. В разделе "Source" выберите "GitHub Actions"
3. GitHub Actions автоматически будет публиковать документацию при каждом пуше

### Локальная сборка

```bash
./gradlew dokkaHtml
```

Документация будет сгенерирована в `docs/api/`.

## Лучшие практики

1. **Документируйте публичные API** — все публичные классы, функции и свойства должны иметь KDoc комментарии
2. **Используйте теги** — `@param`, `@return`, `@throws`, `@see`, `@sample`
3. **Добавляйте примеры** — используйте `@sample` для ссылок на примеры кода
4. **Обновляйте документацию** — при изменении API обновляйте документацию

## Дополнительная информация

- [Официальная документация Dokka](https://kotlinlang.org/docs/dokka-introduction.html)
- [KDoc синтаксис](https://kotlinlang.org/docs/kotlin-doc.html)
- [Примеры KDoc комментариев](https://kotlinlang.org/docs/kotlin-doc.html#block-tags)
