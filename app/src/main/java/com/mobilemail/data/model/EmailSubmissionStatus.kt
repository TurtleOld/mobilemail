package com.mobilemail.data.model

/**
 * Нормализованный статус отправки (JMAP EmailSubmission).
 *
 * Поля серверов могут отличаться, поэтому тут базовый набор:
 * - delivered: сервер считает доставку успешной (если умеет это определять)
 * - failed: сервер считает доставку провалившейся
 * - lastStatusText: человекочитаемая причина (SMTP reply/DSN текст), если доступно
 */
data class EmailSubmissionStatus(
    val id: String,
    val emailId: String? = null,
    val delivered: Boolean? = null,
    val failed: Boolean? = null,
    val lastStatusText: String? = null,
    val raw: Map<String, Any?> = emptyMap()
)
