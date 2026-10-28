package com.mobilemail.data.model

data class JmapEmail(
    val id: String,
    val threadId: String,
    val mailboxIds: Map<String, Boolean>,
    val keywords: Map<String, Boolean>? = null,
    val size: Long,
    val receivedAt: String,
    val hasAttachment: Boolean? = null,
    val preview: String? = null,
    val subject: String? = null,
    val from: List<EmailAddress>? = null,
    val to: List<EmailAddress>? = null,
    val cc: List<EmailAddress>? = null,
    val bcc: List<EmailAddress>? = null,
    val bodyStructure: Any? = null,
    val bodyValues: Map<String, BodyValue>? = null,
    val textBody: List<BodyPart>? = null,
    val htmlBody: List<BodyPart>? = null
)

data class EmailAddress(
    val name: String? = null,
    val email: String
)

data class BodyValue(
    val value: String,
    val isEncodingProblem: Boolean? = null,
    val isTruncated: Boolean? = null
)

data class BodyPart(
    val partId: String,
    val type: String
)

data class EmailQueryResult(
    val ids: List<String>,
    val position: Int,
    val total: Int? = null,
    val queryState: String? = null
)
