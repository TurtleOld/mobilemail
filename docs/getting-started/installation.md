# Установка

Инструкции по установке и настройке MobileMail.

## Требования

- Android 8.0 (API level 26) или выше
- Интернет соединение
- Аккаунт на поддерживаемом почтовом сервере (Stalwart Mail Server)

## Установка из исходников

### Предварительные требования

- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17 или выше
- Android SDK
- Git

### Клонирование репозитория

```bash
git clone https://github.com/TurtleOld/mobilemail.git
cd mobilemail
```

### Открытие проекта

1. Откройте Android Studio
2. Выберите `File > Open`
3. Выберите папку проекта
4. Дождитесь синхронизации Gradle

### Сборка проекта

```bash
./gradlew assembleDebug
```

APK файл будет находиться в `app/build/outputs/apk/debug/`

## Конфигурация

### Настройка OAuth Client ID

Для работы OAuth авторизации необходимо настроить Client ID через переменную окружения
или локальный `.env` файл:

```bash
OAUTH_CLIENT_ID=your-client-id
```

Можно скопировать `.env.example` в `.env` и заменить значение на Client ID,
полученный от администратора почтового сервера. Файл `.env` игнорируется Git.

### Настройка сервера

По умолчанию приложение использует discovery endpoint для автоматического определения OAuth endpoints. Убедитесь, что сервер доступен по адресу:

```
https://your-server.com/.well-known/oauth-authorization-server
```

## Следующие шаги

После установки перейдите к [Быстрому старту](quickstart.md).
