package com.mobilemail.util

object LogRedactor {
    private val sensitiveKeyValueRegex = Regex(
        pattern = "(?i)([\"']?\\b(?:access[_-]?token|refresh[_-]?token|id[_-]?token|device[_-]?code|client[_-]?secret|fcm[_-]?token|token|message[_-]?id|messageId)\\b[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,&}]+)"
    )
    private val bearerTokenRegex = Regex(
        pattern = "(?i)(\\bBearer\\s+)([^\\s,;]+)"
    )
    private val emailRegex = Regex(
        pattern = "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b",
        option = RegexOption.IGNORE_CASE
    )

    fun redact(value: String?): String {
        val raw = value ?: return ""
        if (raw.isBlank()) return raw

        return raw
            .replace(bearerTokenRegex) { match ->
                match.groupValues[1] + "[REDACTED]"
            }
            .replace(sensitiveKeyValueRegex) { match ->
                match.groupValues[1] + "[REDACTED]"
            }
            .replace(emailRegex, "[EMAIL_REDACTED]")
    }
}
