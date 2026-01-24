#!/bin/bash

# Полный просмотр логов с цветной фильтрацией

PACKAGE_NAME='com.mobilemail'

echo "Запуск logcat для приложения: $PACKAGE_NAME"
echo "Нажмите Ctrl+C для выхода"
echo "========================================"

# Очистка логов
adb logcat -c

# Получение PID процесса приложения
PID=$(adb shell pidof -s $PACKAGE_NAME 2>/dev/null | tr -d '\r')

# Запуск logcat с фильтрацией
if [ -n "$PID" ] && [ "$PID" != "" ]; then
    echo "Найден PID процесса: $PID"
    adb logcat \
        *:S \
        AndroidRuntime:E \
        mobilemail:D \
        MainActivity:D \
        MessagesViewModel:D \
        MailRepository:D \
        JmapClient:D \
        "$PID":* \
        | grep --line-buffered -v -E "(chatty|native|Binder|HwBinder)"
else
    echo "Приложение не запущено. Показываю логи без фильтрации по PID"
    adb logcat \
        *:S \
        AndroidRuntime:E \
        mobilemail:D \
        MainActivity:D \
        MessagesViewModel:D \
        MailRepository:D \
        JmapClient:D \
        | grep --line-buffered -v -E "(chatty|native|Binder|HwBinder)"
fi
