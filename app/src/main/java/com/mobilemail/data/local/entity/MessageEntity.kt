package com.mobilemail.data.local.entity

import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.mobilemail.data.local.converter.DateConverter
import java.util.Date

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["folderId", "accountId", "date"]),
        Index(value = ["accountId", "folderId", "date"]),
        Index(value = ["accountId"]),
        Index(value = ["folderId"]),
        Index(value = ["date"]),
        Index(value = ["accountId", "threadId"]),
        Index(value = ["accountId", "fromEmail"]),
        Index(value = ["syncedAt"])
    ]
)
@TypeConverters(DateConverter::class)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val threadId: String,
    val folderId: String,
    val accountId: String,
    val fromName: String?,
    val fromEmail: String,
    val subject: String,
    val snippet: String,
    val date: Date,
    val isUnread: Boolean,
    val isStarred: Boolean,
    val isImportant: Boolean,
    val hasAttachments: Boolean,
    val size: Long,
    val textBody: String?,
    val htmlBody: String?,
    val syncedAt: Long = System.currentTimeMillis()
)
