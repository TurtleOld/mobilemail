package com.mobilemail.data.model

data class JmapMailbox(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val role: String? = null,
    val sortOrder: Int? = null,
    val totalEmails: Int? = null,
    val unreadEmails: Int? = null,
    val totalThreads: Int? = null,
    val unreadThreads: Int? = null
)
