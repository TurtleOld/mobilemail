package com.mobilemail.ui.newmessage

import com.mobilemail.domain.model.EmailAddress
import com.mobilemail.domain.model.MessageDetail
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ComposePrefill(
    val to: String = "",
    val subject: String = "",
    val body: String = ""
)

enum class ReplyAction {
    REPLY,
    REPLY_ALL,
    FORWARD
}

object ComposePrefillStore {
    private val drafts = ConcurrentHashMap<String, ComposePrefill>()

    fun createReplyDraft(
        message: MessageDetail,
        currentUserEmail: String,
        action: ReplyAction
    ): String {
        val token = UUID.randomUUID().toString()
        drafts[token] = when (action) {
            ReplyAction.REPLY -> ComposePrefill(
                to = message.from.email,
                subject = withReplyPrefix(message.subject),
                body = quotedBody(message)
            )
            ReplyAction.REPLY_ALL -> ComposePrefill(
                to = buildReplyAllRecipients(message, currentUserEmail).joinToString(", "),
                subject = withReplyPrefix(message.subject),
                body = quotedBody(message)
            )
            ReplyAction.FORWARD -> ComposePrefill(
                to = "",
                subject = withForwardPrefix(message.subject),
                body = forwardedBody(message)
            )
        }
        return token
    }

    fun consume(token: String?): ComposePrefill? {
        if (token.isNullOrBlank() || token == "-") return null
        return drafts.remove(token)
    }

    private fun withReplyPrefix(subject: String): String {
        return if (subject.startsWith("Re:", ignoreCase = true)) subject else "Re: $subject"
    }

    private fun withForwardPrefix(subject: String): String {
        return if (subject.startsWith("Fwd:", ignoreCase = true)) subject else "Fwd: $subject"
    }

    private fun buildReplyAllRecipients(message: MessageDetail, currentUserEmail: String): List<String> {
        val normalizedCurrentUser = currentUserEmail.trim().lowercase()
        return buildList {
            addIfNeeded(message.from, normalizedCurrentUser)
            message.to.forEach { addIfNeeded(it, normalizedCurrentUser) }
            message.cc.orEmpty().forEach { addIfNeeded(it, normalizedCurrentUser) }
        }.distinct()
    }

    private fun MutableList<String>.addIfNeeded(address: EmailAddress, currentUserEmail: String) {
        val normalized = address.email.trim().lowercase()
        if (normalized.isNotBlank() && normalized != currentUserEmail && normalized !in this) {
            add(address.email)
        }
    }

    private fun quotedBody(message: MessageDetail): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val header = "${formatter.format(message.date)} ${message.from.name ?: message.from.email} wrote:"
        val plainBody = message.body.text?.takeIf { it.isNotBlank() }
            ?: stripHtml(message.body.html)
        val quoted = plainBody
            .lines()
            .joinToString("\n") { line -> "> $line" }
            .trim()
        return "\n\n$header\n$quoted"
    }

    private fun forwardedBody(message: MessageDetail): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val plainBody = message.body.text?.takeIf { it.isNotBlank() }
            ?: stripHtml(message.body.html)
        return buildString {
            append("\n\n---------- Forwarded message ----------\n")
            append("From: ")
            append(message.from.name ?: message.from.email)
            append(" <")
            append(message.from.email)
            append(">\n")
            append("Date: ")
            append(formatter.format(message.date))
            append("\n")
            append("Subject: ")
            append(message.subject)
            append("\n")
            append("To: ")
            append(message.to.joinToString(", ") { it.email })
            append("\n\n")
            append(plainBody)
        }
    }

    private fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}
