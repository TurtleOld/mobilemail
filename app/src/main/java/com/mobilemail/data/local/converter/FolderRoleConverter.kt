package com.mobilemail.data.local.converter

import androidx.room.TypeConverter
import com.mobilemail.data.model.FolderRole

class FolderRoleConverter {
    @TypeConverter
    fun fromRole(role: FolderRole): String {
        return role.name
    }

    @TypeConverter
    fun toRole(role: String): FolderRole {
        return FolderRole.valueOf(role)
    }
}
