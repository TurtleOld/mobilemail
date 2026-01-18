package com.mobilemail.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Date.format(pattern: String = "dd.MM.yyyy HH:mm", locale: Locale = Locale.getDefault()): String {
    return SimpleDateFormat(pattern, locale).format(this)
}

fun Date.formatShort(): String = format("dd.MM.yyyy")

fun Date.formatTime(): String = format("HH:mm")

fun Date.formatFull(): String = format("dd.MM.yyyy HH:mm:ss")

fun Date.isToday(): Boolean {
    val today = Date()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(this) == sdf.format(today)
}

fun Date.isYesterday(): Boolean {
    val yesterday = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(this) == sdf.format(yesterday)
}

fun Date.formatRelative(): String = when {
    isToday() -> formatTime()
    isYesterday() -> "Вчера"
    else -> formatShort()
}
