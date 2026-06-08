package com.mobilemail.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(tokenizer = "unicode61")
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    val messageId: String,
    val accountId: String,
    val folderId: String,
    val fromName: String?,
    val fromEmail: String,
    val subject: String,
    val snippet: String,
    val textBody: String?,
    val htmlBody: String?
)
