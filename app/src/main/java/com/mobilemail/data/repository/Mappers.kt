package com.mobilemail.data.repository

import com.mobilemail.data.local.entity.FolderEntity
import com.mobilemail.data.local.entity.MessageEntity
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.MessageBody
import com.mobilemail.data.model.MessageDetail
import com.mobilemail.data.model.MessageFlags
import com.mobilemail.data.model.MessageListItem

object Mappers {
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
            unreadCount = this.unreadCount
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
