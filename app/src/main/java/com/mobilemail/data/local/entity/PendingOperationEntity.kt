package com.mobilemail.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val server: String,
    val email: String,
    val accountId: String,
    val payload: String,
    val status: String,
    val attemptCount: Int,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
)
