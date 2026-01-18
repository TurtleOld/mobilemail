package com.mobilemail.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.mobilemail.data.local.converter.FolderRoleConverter
import com.mobilemail.data.model.FolderRole

@Entity(tableName = "folders")
@TypeConverters(FolderRoleConverter::class)
data class FolderEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val name: String,
    val role: FolderRole,
    val unreadCount: Int = 0,
    val syncedAt: Long = System.currentTimeMillis()
)
