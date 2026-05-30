# Performance, UX и стек приложения

Документ для этапа 6 модернизации: архитектура perf-части, baseline profiles и актуальный технологический стек MobileMail.

См. также: [toolchain.md](toolchain.md) (версии Gradle/AGP/SDK), [modernization-baseline.md](modernization-baseline.md) (smoke после волн).

---

## Стек приложения

### Платформа и сборка

| Слой | Технология | Версия (pinned) |
| --- | --- | --- |
| Язык | Kotlin | 2.3.21 |
| JVM | Java | 17 |
| Сборка | Gradle | 9.4.1 |
| Android | AGP | 9.2.0 |
| Codegen | KSP (Room) | 2.3.9 |
| `compileSdk` | Android API | 36.1 |
| `targetSdk` | Android API | 36 |
| `minSdk` | Android API | 26 |
| Документация | Dokka | 2.2.0 |

Kotlin подключается через встроенную поддержку AGP 9 (`org.jetbrains.kotlin.plugin.compose`).

### UI

| Компонент | Библиотека |
| --- | --- |
| UI toolkit | Jetpack Compose (BOM 2026.05.00) |
| Дизайн | Material Design 3 |
| Навигация | Navigation Compose 2.9.8 |
| Состояние экранов | ViewModel + `lifecycle-runtime-compose` |
| Общий UI-контракт | `FeatureScreenUiState`, `FeatureScreenEffects` |
| Списки | Paging 3 + `paging-compose` |
| Изображения | Coil Compose |
| HTML-тело письма | `AndroidView` + `WebView` (пакет `ui/messagedetail/content`) |

### Архитектура кода

Один модуль `:app`, слои по пакетам:

```
com.mobilemail/
├── MainActivity.kt              # lifecycle, push intent
├── MobileMailApplication.kt
├── domain/
│   ├── usecase/                 # сценарии (сессия, push, logout, startup)
│   └── port/                    # порты (например push topics)
├── data/
│   ├── jmap/                    # JMAP-клиент, OAuth JMAP
│   ├── oauth/                   # Device Flow, refresh, TokenStore
│   ├── repository/              # Mail, Search, Compose, вложения
│   ├── local/                   # Room (сообщения, папки, offline queue)
│   ├── sync/                    # WorkManager, offline queue
│   ├── security/                # Keystore, PIN, credentials
│   └── preferences/             # DataStore
├── notifications/               # FCM, ntfy, push navigation
└── ui/
    ├── navigation/              # AppNavGraph, AppNavigationHost, routes
    ├── login | messages | messagedetail | search | newmessage | outbox
    ├── security/                # PIN lock / setup
    ├── settings/
    ├── common/
    └── theme/
```

Дополнительный модуль `:baseline-profile` — генерация startup Baseline Profile (Macrobenchmark).

### Сеть и протокол

| Назначение | Технология |
| --- | --- |
| Почта | **JMAP** (Stalwart) через `JmapClient` / `JmapApi` |
| HTTP | OkHttp 4.12.0 |
| JSON | `org.json` + Gson (legacy/вспомогательно) |
| OAuth | OAuth 2.0 Device Authorization Grant (`DeviceFlowClient`) |

### Локальные данные и фон

| Назначение | Технология |
| --- | --- |
| Кэш / офлайн | Room 2.8.4 |
| Настройки | DataStore Preferences |
| Фоновая очередь | WorkManager 2.11.2 (`OfflineQueueWorker`, `OfflineQueueWorkPolicy`) |
| Асинхронность | Kotlin Coroutines + Flow |

### Безопасность и push

| Назначение | Технология |
| --- | --- |
| Секреты / токены | Android Keystore (`KeystoreSecureStore`) |
| Блокировка приложения | Biometric + PIN (`PinManager`) |
| Push доставка | Firebase Cloud Messaging (BOM 34.11.0) |
| Топики / маршрутизация | ntfy (`NtfyClient`, `BuildConfig` NTFY_*) |
| Deep link / cold start | `PublishPushNavigationFromIntentUseCase`, `AppNavigationHost` |

### Производительность (Stage 6)

| Компонент | Назначение |
| --- | --- |
| `androidx.profileinstaller` | Установка baseline profile в runtime |
| `:baseline-profile` | `BaselineProfileRule` — cold start |
| Scroll interop | `MessageDetailScrollInterop` — WebView ↔ parent `Column` |

> Для AGP 9 плагин Baseline Profile пока требует `android.newDsl=false` в `gradle.properties` (см. комментарий в файле).

### Тестирование

| Тип | Стек |
| --- | --- |
| Unit | JUnit 4, MockWebServer, Turbine, Room Testing |
| UI (Compose) | `ui-test-junit4`, Espresso |
| Instrumented | AndroidJUnit, push cold-start test |
| Baseline / macro | `benchmark-macro-junit4`, UiAutomator |

---

## Message detail: прокрутка HTML

Пакет `com.mobilemail.ui.messagedetail.content`:

| Файл | Ответственность |
| --- | --- |
| `MessageBodySection.kt` | HTML vs plain text, блокировка remote content |
| `HtmlMessageSanitizer.kt` | Удаление опасного HTML |
| `HtmlDocumentComposer.kt` | Responsive HTML-обёртка |
| `HtmlMessageWebView.kt` | Compose `AndroidView` + загрузка |
| `HtmlMessageWebViewPolicy.kt` | Настройки WebView, SafeBrowsing, scroll handoff |
| `HtmlWebViewHeightMeasurer.kt` | JS-измерение высоты контента |
| `MessageDetailScrollInterop.kt` | Передача жестов родителю |
| `ExternalUriOpener.kt` | Безопасное открытие ссылок |
| `RemoteContentAllowanceStore.kt` | Allowlist внешнего контента |

**Правило скролла:** если `WebView.canScrollVertically` в обе стороны false (высота = контент), вертикальные жесты обрабатывает родительский `Column` с `verticalScroll`. Если внутри WebView есть overflow — скролл остаётся в WebView.

Unit-тесты: `MessageDetailScrollInteropTest`, `HtmlMessageSanitizerTest`.

---

## Baseline profiles

- Генератор: модуль `:baseline-profile` (`StartupBaselineProfile`)
- Потребитель: `:app` — `baselineProfile(project(":baseline-profile"))` + `profileinstaller`

Генерация на подключённом устройстве или эмуляторе:

```bash
./gradlew :app:generateBaselineProfile
```

Профиль попадает в release-сборку после успешной генерации; в CI по умолчанию не запускается.

---

## Бюджеты производительности (ориентиры)

Измерять на mid-range устройстве, **release**-сборка. Уточнять после macrobenchmark.

| Метрика | Цель (ориентир) |
| --- | --- |
| Cold start → первый кадр | < 800 ms |
| Inbox time-to-interactive | < 2.5 s |
| Скролл списка писем | без устойчивых просадок > 16 ms |
| Длинное HTML-письмо | плавный скролл экрана без «залипания» в WebView |

---

## Smoke после perf-изменений

1. `./gradlew :app:testDebugUnitTest`
2. `./gradlew :app:assembleDebug`
3. Ручная проверка: длинное HTML-письмо — прокрутка сверху вниз
4. Plain-text письмо с ссылками
5. (опционально) `./gradlew :app:generateBaselineProfile` на устройстве
6. (опционально) `./gradlew :app:assembleRelease` после генерации profile
