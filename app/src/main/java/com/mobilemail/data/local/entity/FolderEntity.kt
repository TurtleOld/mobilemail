package com.mobilemail.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.TypeConverters
import com.mobilemail.data.local.converter.FolderRoleConverter
import com.mobilemail.data.model.FolderRole

@Entity(
    tableName = "folders",
    primaryKeys = ["localAccountId", "id"],
    indices = [Index(value = ["localAccountId"])]
)
@TypeConverters(FolderRoleConverter::class)
data class FolderEntity(
    val localAccountId: String,
    val id: String,
    val accountId: String,
    val name: String,
    val role: FolderRole,
    val unreadCount: Int = 0,
    val queryState: String? = null,
    val syncedAt: Long = System.currentTimeMillis()
)
