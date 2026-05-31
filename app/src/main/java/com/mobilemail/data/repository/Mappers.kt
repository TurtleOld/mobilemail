package com.mobilemail.data.repository

import com.mobilemail.data.local.entity.FolderEntity
import com.mobilemail.data.local.entity.MessageEntity
import com.mobilemail.data.model.Attachment
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.FolderRole
import com.mobilemail.data.model.JmapAccount
import com.mobilemail.data.model.JmapEmail
import com.mobilemail.data.model.JmapMailbox
import com.mobilemail.data.model.MessageBody
import com.mobilemail.data.model.MessageDetail
import com.mobilemail.data.model.MessageFlags
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.domain.model.Account as DomainAccount
import java.time.Instant
import java.util.Date

object Mappers {

    fun JmapMailbox.toFolder(): Folder = Folder(
        id = id,
        name = name,
        role = when (role) {
            "inbox" -> FolderRole.INBOX
            "sent" -> FolderRole.SENT
            "drafts" -> FolderRole.DRAFTS
            "trash" -> FolderRole.TRASH
            "spam", "junk" -> FolderRole.SPAM
            "archive" -> FolderRole.ARCHIVE
            else -> FolderRole.CUSTOM
        },
        unreadCount = unreadEmails ?: 0
    )

    fun JmapEmail.toMessageListItem(parseAttachments: (Any?) -> List<Attachment>): MessageListItem {
        val from = from?.firstOrNull() ?: EmailAddress(email = "unknown")
        val cleanFrom = EmailAddress(
            name = from.name?.takeIf { it.isNotBlank() && it != "null" },
            email = from.email.takeIf { it.isNotBlank() && it != "null" } ?: "unknown"
        )
        val isUnread = keywords?.get("\$seen") != true
        val isStarred = keywords?.get("\$flagged") == true
        val isImportant = keywords?.get("\$important") == true
        val hasRealAttachments = if (bodyStructure != null) {
            runCatching { parseAttachments(bodyStructure).isNotEmpty() }.getOrDefault(hasAttachment == true)
        } else {
            hasAttachment == true
        }
        return MessageListItem(
            id = id,
            threadId = threadId,
            from = cleanFrom,
            subject = subject ?: "(без темы)",
            snippet = preview ?: "",
            date = parseDateSafe(receivedAt),
            flags = MessageFlags(
                unread = isUnread,
                starred = isStarred,
                important = isImportant,
                hasAttachments = hasRealAttachments
            ),
            size = size
        )
    }

    fun JmapEmail.toMessageDetail(
        parseAttachments: (Any?) -> List<Attachment>,
        cachedReadStatus: Boolean? = null
    ): MessageDetail {
        val cleanFrom = cleanAddress(from?.firstOrNull())
        val attachments = if (bodyStructure != null) {
            runCatching { parseAttachments(bodyStructure) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val (textBody, htmlBody) = parseBodyText()
        val isUnreadFromServer = keywords?.get("\$seen") != true
        val isStarred = keywords?.get("\$flagged") == true
        val isImportant = keywords?.get("\$important") == true

        return MessageDetail(
            id = id,
            threadId = threadId,
            mailboxIds = mailboxIds.keys,
            from = cleanFrom,
            to = to?.map { cleanAddress(it) } ?: emptyList(),
            cc = cc?.map { cleanAddress(it) },
            bcc = bcc?.map { cleanAddress(it) },
            subject = subject ?: "(без темы)",
            date = parseDateSafe(receivedAt),
            body = MessageBody(text = textBody, html = htmlBody),
            attachments = attachments,
            flags = MessageFlags(
                unread = cachedReadStatus ?: isUnreadFromServer,
                starred = isStarred,
                important = isImportant,
                hasAttachments = hasAttachment == true || attachments.isNotEmpty()
            )
        )
    }

    private fun cleanAddress(addr: com.mobilemail.data.model.EmailAddress?): EmailAddress = EmailAddress(
        name = addr?.name?.takeIf { it.isNotBlank() && it != "null" },
        email = addr?.email?.takeIf { it.isNotBlank() && it != "null" } ?: "unknown"
    )

    private fun JmapEmail.parseBodyText(): Pair<String?, String?> {
        var textBody: String? = null
        var htmlBody: String? = null

        this.textBody?.forEach { part -> bodyValues?.get(part.partId)?.let { textBody = it.value } }
        this.htmlBody?.forEach { part -> bodyValues?.get(part.partId)?.let { htmlBody = it.value } }

        if (textBody == null && htmlBody == null) {
            bodyValues?.values?.firstOrNull()?.let {
                val content = it.value.trim()
                val looksLikeHtml = content.startsWith("<") &&
                    (content.contains("<html") || content.contains("<body") ||
                     content.contains("<div") || content.contains("<p"))
                if (looksLikeHtml) htmlBody = it.value else textBody = it.value
            }
        }
        return Pair(textBody, htmlBody)
    }

    fun JmapAccount.toDomainAccount(accountId: String): DomainAccount {
        val resolvedEmail = when {
            !username.isNullOrBlank() -> username
            name.isNotBlank() && name.contains("@") -> name
            accountId.isNotBlank() && accountId.contains("@") -> accountId
            accountId.isNotBlank() -> accountId
            else -> name
        }
        return DomainAccount(
            id = accountId,
            email = resolvedEmail,
            displayName = name.ifBlank { resolvedEmail }
        )
    }

    private fun parseDateSafe(receivedAt: String?): Date = try {
        Date.from(Instant.parse(receivedAt))
    } catch (e: Exception) {
        Date()
    }
    fun FolderEntity.toFolder(): Folder {
        return Folder(
            id = this.id,
            name = this.name,
            role = this.role,
            unreadCount = this.unreadCount
        )
    }

    fun Folder.toFolderEntity(accountId: String): FolderEntity {
        return FolderEntity(
            id = this.id,
            accountId = accountId,
            name = this.name,
            role = this.role,
            unreadCount = this.unreadCount,
            queryState = null
        )
    }

    fun MessageEntity.toMessageListItem(): MessageListItem {
        return MessageListItem(
            id = this.id,
            threadId = this.threadId,
            from = EmailAddress(name = this.fromName, email = this.fromEmail),
            subject = this.subject,
            snippet = this.snippet,
            date = this.date,
            flags = MessageFlags(
                unread = this.isUnread,
                starred = this.isStarred,
                important = this.isImportant,
                hasAttachments = this.hasAttachments
            ),
            size = this.size
        )
    }

    fun MessageEntity.toMessageDetail(): MessageDetail {
        return MessageDetail(
            id = this.id,
            threadId = this.threadId,
            from = EmailAddress(name = this.fromName, email = this.fromEmail),
            to = emptyList(), // TODO: сохранять в отдельной таблице
            cc = null,
            bcc = null,
            subject = this.subject,
            date = this.date,
            body = MessageBody(text = this.textBody, html = this.htmlBody),
            attachments = emptyList(), // TODO: парсить из bodyStructure
            flags = MessageFlags(
                unread = this.isUnread,
                starred = this.isStarred,
                important = this.isImportant,
                hasAttachments = this.hasAttachments
            )
        )
    }

    fun MessageListItem.toMessageEntity(folderId: String, accountId: String): MessageEntity {
        return MessageEntity(
            id = this.id,
            threadId = this.threadId,
            folderId = folderId,
            accountId = accountId,
            fromName = this.from.name,
            fromEmail = this.from.email,
            subject = this.subject,
            snippet = this.snippet,
            date = this.date,
            isUnread = this.flags.unread,
            isStarred = this.flags.starred,
            isImportant = this.flags.important,
            hasAttachments = this.flags.hasAttachments,
            size = this.size,
            textBody = null,
            htmlBody = null
        )
    }

    fun MessageDetail.toMessageEntity(folderId: String, accountId: String): MessageEntity {
        return MessageEntity(
            id = this.id,
            threadId = this.threadId,
            folderId = folderId,
            accountId = accountId,
            fromName = this.from.name,
            fromEmail = this.from.email,
            subject = this.subject,
            snippet = "", // TODO: извлекать из body
            date = this.date,
            isUnread = this.flags.unread,
            isStarred = this.flags.starred,
            isImportant = this.flags.important,
            hasAttachments = this.flags.hasAttachments,
            size = 0, // TODO: вычислять размер
            textBody = this.body.text,
            htmlBody = this.body.html
        )
    }
}
