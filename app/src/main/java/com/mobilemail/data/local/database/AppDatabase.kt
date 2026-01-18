package com.mobilemail.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mobilemail.data.local.converter.DateConverter
import com.mobilemail.data.local.converter.FolderRoleConverter
import com.mobilemail.data.local.dao.FolderDao
import com.mobilemail.data.local.dao.MessageDao
import com.mobilemail.data.local.entity.FolderEntity
import com.mobilemail.data.local.entity.MessageEntity

@Database(
    entities = [MessageEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class, FolderRoleConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun folderDao(): FolderDao
    
    companion object {
        const val DATABASE_NAME = "mobilemail_db"
    }
}
