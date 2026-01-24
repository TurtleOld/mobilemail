#!/bin/bash

# Скрипт для просмотра логов подключения к серверу

PACKAGE_NAME='com.mobilemail'

echo "📋 Просмотр логов подключения к серверу"
echo "Фильтр: JmapClient, LoginViewModel, MailRepository"
echo "Нажмите Ctrl+C для выхода"
echo "========================================"

# Очистка старых логов
adb logcat -c

# Показ логов с фильтрацией по тегам приложения
adb logcat -s JmapClient:D LoginViewModel:D MailRepository:D AndroidRuntime:E *:S
