package com.mobilemail.domain.model

import com.mobilemail.data.model.Account as DataAccount
import com.mobilemail.data.model.Attachment as DataAttachment
import com.mobilemail.data.model.EmailAddress as DataEmailAddress
import com.mobilemail.data.model.EmailSubmissionStatus as DataEmailSubmissionStatus
import com.mobilemail.data.model.Folder as DataFolder
import com.mobilemail.data.model.FolderRole as DataFolderRole
import com.mobilemail.data.model.MessageBody as DataMessageBody
import com.mobilemail.data.model.MessageDetail as DataMessageDetail
import com.mobilemail.data.model.MessageFlags as DataMessageFlags
import com.mobilemail.data.model.MessageListItem as DataMessageListItem

fun DataAccount.toDomain() = Account(id = id, email = email, displayName = displayName)

fun DataFolder.toDomain() = Folder(
    id = id,
    name = name,
    role = role.toDomain(),
    unreadCount = unreadCount
)

fun DataFolderRole.toDomain() = when (this) {
    DataFolderRole.INBOX -> FolderRole.INBOX
    DataFolderRole.SENT -> FolderRole.SENT
    DataFolderRole.DRAFTS -> FolderRole.DRAFTS
    DataFolderRole.TRASH -> FolderRole.TRASH
    DataFolderRole.SPAM -> FolderRole.SPAM
    DataFolderRole.ARCHIVE -> FolderRole.ARCHIVE
    DataFolderRole.CUSTOM -> FolderRole.CUSTOM
}

fun DataEmailAddress.toDomain() = EmailAddress(name = name, email = email)

fun DataMessageFlags.toDomain() = MessageFlags(
    unread = unread,
    starred = starred,
    important = important,
    hasAttachments = hasAttachments
)

fun DataMessageBody.toDomain() = MessageBody(text = text, html = html)

fun DataAttachment.toDomain() = Attachment(
    id = id,
    filename = filename,
    mime = mime,
    size = size,
    localFilePath = localFilePath,
    isUploaded = isUploaded
)

fun Attachment.toData() = DataAttachment(
    id = id,
    filename = filename,
    mime = mime,
    size = size,
    localFilePath = localFilePath,
    isUploaded = isUploaded
)

fun DataMessageListItem.toDomain() = MessageListItem(
    id = id,
    threadId = threadId,
    from = from.toDomain(),
    subject = subject,
    snippet = snippet,
    date = date,
    flags = flags.toDomain(),
    size = size
)

fun DataMessageDetail.toDomain() = MessageDetail(
    id = id,
    threadId = threadId,
    mailboxIds = mailboxIds,
    from = from.toDomain(),
    to = to.map { it.toDomain() },
    cc = cc?.map { it.toDomain() },
    bcc = bcc?.map { it.toDomain() },
    subject = subject,
    date = date,
    body = body.toDomain(),
    attachments = attachments.map { it.toDomain() },
    flags = flags.toDomain()
)

fun DataEmailSubmissionStatus.toDomain() = EmailSubmissionStatus(
    id = id,
    emailId = emailId,
    delivered = delivered,
    failed = failed,
    lastStatusText = lastStatusText,
    raw = raw
)
