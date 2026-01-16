package com.mobilemail.data.model

import java.util.Date

data class MessageListItem(
    val id: String,
    val threadId: String,
    val from: EmailAddress,
    val subject: String,
    val snippet: String,
    val date: Date,
    val flags: MessageFlags,
    val size: Long
)

data class MessageDetail(
    val id: String,
    val threadId: String,
    val from: EmailAddress,
    val to: List<EmailAddress>,
    val cc: List<EmailAddress>? = null,
    val bcc: List<EmailAddress>? = null,
    val subject: String,
    val date: Date,
    val body: MessageBody,
    val attachments: List<Attachment> = emptyList(),
    val flags: MessageFlags
)

data class MessageFlags(
    val unread: Boolean,
    val starred: Boolean = false,
    val important: Boolean = false,
    val hasAttachments: Boolean = false
)

data class MessageBody(
    val text: String? = null,
    val html: String? = null
)

data class Attachment(
    val id: String,
    val filename: String,
    val mime: String,
    val size: Long
)
