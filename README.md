# MobileMail - Мобильный клиент почты для Stalwart

Мобильное приложение для работы с почтовым сервером Stalwart через JMAP протокол.

## Технологии

Полный стек (версии, слои, perf): [docs/guides/performance.md](docs/guides/performance.md). Toolchain/SDK: [docs/guides/toolchain.md](docs/guides/toolchain.md).

- **Kotlin 2.3** + **Jetpack Compose** (Material 3)
- **JMAP** (Stalwart) + **OkHttp**
- **Room**, **DataStore**, **WorkManager**, **Paging**
- **OAuth Device Flow**, **FCM** + **ntfy** для push
- **Navigation Compose**, **ViewModel**, domain use-case’ы

## Функциональность

- ✅ Авторизация на сервере Stalwart (сервер, логин, пароль)
- ✅ Просмотр списка папок
- ✅ Просмотр списка писем в папках
- ✅ Просмотр содержимого письма
- ✅ Отображение HTML писем

## Структура проекта

```
app/
├── src/main/java/com/mobilemail/
│   ├── data/
│   │   ├── jmap/          # JMAP клиент
│   │   ├── model/         # Модели данных
│   │   └── repository/    # Репозиторий для работы с данными
│   └── ui/
│       ├── login/         # Экран авторизации
│       ├── messages/      # Экран списка писем
│       ├── messagedetail/ # Экран просмотра письма
│       └── theme/         # Тема приложения
└── MainActivity.kt        # Главная активность
```

## Настройка

1. Убедитесь, что у вас установлен Android Studio с поддержкой Kotlin и Jetpack Compose
2. Откройте проект в Android Studio
3. Дождитесь синхронизации Gradle
4. Запустите приложение на эмуляторе или устройстве

## Использование

1. При запуске приложения откроется экран авторизации
2. Введите:
   - **Адрес сервера**: например, `http://stalwart:8080` или `http://192.168.1.100:8080`
   - **Логин**: ваш email или username
   - **Пароль**: ваш пароль
3. Нажмите "Войти"
4. После успешной авторизации откроется список писем
5. Выберите папку слева для просмотра писем
6. Нажмите на письмо для просмотра содержимого

## Требования

- Android 8.0 (API 26) или выше
- Подключение к интернету
- Доступ к серверу Stalwart

## Разработка

Проект использует:
- Kotlin Coroutines для асинхронных операций
- ViewModel для управления состоянием
- Navigation Compose для навигации
- OkHttp для HTTP запросов
- JSON для парсинга ответов JMAP

## Документация

- [performance.md](docs/guides/performance.md) — стек приложения, perf/UX, baseline profiles
- [toolchain.md](docs/guides/toolchain.md) — Gradle, AGP, SDK
- [modernization-baseline.md](docs/guides/modernization-baseline.md) — smoke-чеклисты

Документация API генерируется из KDoc комментариев в коде с использованием [Dokka](https://kotlinlang.org/docs/dokka-introduction.html).

```bash
# Генерация документации
./gradlew dokkaHtml

# Или через Makefile
make docs-build
```

Документация будет сгенерирована в `docs/api/`. Откройте `docs/api/index.html` в браузере для просмотра.

**Онлайн документация:** [https://turtleold.github.io/mobilemail/](https://turtleold.github.io/mobilemail/)

См. [DOCUMENTATION.md](DOCUMENTATION.md) для подробной информации.

## Документация API

Документация API генерируется автоматически из KDoc комментариев и публикуется на [GitHub Pages](https://turtleold.github.io/mobilemail/).

Для локальной генерации:

```bash
./gradlew dokkaHtml
```

Документация будет сгенерирована в `docs/api/`.

## Лицензия

Проект создан для личного использования.
