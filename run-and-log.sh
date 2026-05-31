#!/bin/bash

# Установка, запуск приложения и просмотр логов в одном скрипте

PACKAGE_NAME='com.mobilemail'

echo "🔨 Сборка и установка приложения..."
./gradlew installDebug

if [ $? -ne 0 ]; then
    echo "❌ Ошибка при сборке!"
    exit 1
fi

echo "🚀 Запуск приложения..."
adb shell am start -n $PACKAGE_NAME/.MainActivity

sleep 1

echo "📋 Просмотр логов (Ctrl+C для выхода)..."
echo "========================================"

# Очистка старых логов
adb logcat -c

# Показ логов с фильтрацией
adb logcat | grep --line-buffered -E "(mobilemail|$PACKAGE_NAME|AndroidRuntime|FATAL|ERROR)" --color=always
