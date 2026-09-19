package com.mobilemail.data.update

import android.content.Intent

/**
 * Хранит системное подтверждение установки, которое нельзя запускать из фона.
 * Пока тикет 05 не восстанавливает установку, отложенное подтверждение живёт
 * в процессе и запускается только по явному возвращению приложения в foreground.
 */
object UpdateInstallUserActionHolder {
    @Volatile
    private var pendingIntent: Intent? = null

    fun store(intent: Intent?) {
        pendingIntent = intent
    }

    fun take(): Intent? {
        val current = pendingIntent
        pendingIntent = null
        return current
    }

    fun restore(intent: Intent) {
        if (pendingIntent == null) {
            pendingIntent = intent
        }
    }
}
