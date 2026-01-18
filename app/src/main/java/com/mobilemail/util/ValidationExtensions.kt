package com.mobilemail.util

import java.net.URL

fun String.isValidEmail(): Boolean {
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    return emailRegex.matches(this)
}

fun String.isValidUrl(): Boolean {
    return try {
        URL(this)
        true
    } catch (e: Exception) {
        false
    }
}

fun String.isValidHttpUrl(): Boolean {
    return (this.startsWith("http://") || this.startsWith("https://")) && isValidUrl()
}

fun String.normalizeUrl(): String {
    return this.trimEnd('/').trim()
}

fun String?.isNullOrBlank(): Boolean {
    return this == null || this.isBlank()
}

fun String?.orDefault(default: String): String {
    return if (this == null || this.isBlank()) default else this
}
