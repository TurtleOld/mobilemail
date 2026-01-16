package com.mobilemail.data.model

import com.google.gson.annotations.SerializedName

data class JmapMailbox(
    @SerializedName('id')
    val id: String,
    @SerializedName('name')
    val name: String,
    @SerializedName('parentId')
    val parentId: String? = null,
    @SerializedName('role')
    val role: String? = null,
    @SerializedName('sortOrder')
    val sortOrder: Int? = null,
    @SerializedName('totalEmails')
    val totalEmails: Int? = null,
    @SerializedName('unreadEmails')
    val unreadEmails: Int? = null,
    @SerializedName('totalThreads')
    val totalThreads: Int? = null,
    @SerializedName('unreadThreads')
    val unreadThreads: Int? = null
)
