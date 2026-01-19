# Архитектура

Обзор архитектуры MobileMail.

## Структура проекта

```
app/src/main/java/com/mobilemail/
├── data/
│   ├── oauth/          # OAuth авторизация
│   ├── jmap/           # JMAP клиент
│   ├── repository/     # Репозитории данных
│   ├── model/          # Модели данных
│   └── security/       # Безопасность
├── ui/                  # UI компоненты
└── util/                # Утилиты
```

## Основные компоненты

### OAuth слой

- `OAuthDiscovery` — получение метаданных
- `DeviceFlowClient` — Device Authorization Grant
- `TokenStore` — хранение токенов
- `OAuthTokenRefresh` — обновление токенов

### JMAP слой

- `JmapOAuthClient` — клиент с OAuth поддержкой
- `JmapClient` — базовый клиент (legacy)

### UI слой

- Jetpack Compose для UI
- ViewModel для бизнес-логики
- StateFlow для состояния

## Потоки данных

### Авторизация

1. Discovery → получение метаданных
2. Device Code → запрос кода
3. Polling → получение токена
4. Сохранение → TokenStore
5. JMAP → использование токена

### Работа с почтой

1. Запрос → JmapOAuthClient
2. Автообновление токена при 401/403
3. Ответ → Repository
4. UI обновление → StateFlow

## Зависимости

- OkHttp — HTTP клиент
- Room — локальная БД
- Coroutines — асинхронность
- Jetpack Compose — UI
