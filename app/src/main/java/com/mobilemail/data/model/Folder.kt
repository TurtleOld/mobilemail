package com.mobilemail.data.model

data class Folder(
    val id: String,
    val name: String,
    val role: FolderRole,
    val unreadCount: Int = 0
)

enum class FolderRole {
    INBOX,
    SENT,
    DRAFTS,
    TRASH,
    SPAM,
    ARCHIVE,
    CUSTOM
}
