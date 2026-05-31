#!/bin/bash

# Скрипт для просмотра логов приложения MobileMail в консоли VS Code

PACKAGE_NAME='com.mobilemail'

# Очистка предыдущих логов
adb logcat -c

# Получение PID процесса приложения
PID=$(adb shell pidof -s $PACKAGE_NAME)

if [ -z "$PID" ]; then
    echo "Приложение не запущено. Запускаю..."
    adb shell am start -n $PACKAGE_NAME/.MainActivity
    sleep 2
    PID=$(adb shell pidof -s $PACKAGE_NAME)
fi

if [ -z "$PID" ]; then
    echo "Не удалось найти процесс приложения. Показываю все логи с фильтром по пакету..."
    adb logcat | grep --line-buffered -E "($PACKAGE_NAME|AndroidRuntime|FATAL|mobilemail)"
else
    echo "Показываю логи для PID: $PID"
    echo "Нажмите Ctrl+C для выхода"
    echo "----------------------------------------"
    # Фильтрация логов по PID процесса
    adb logcat | grep --line-buffered "$PID"
fi
