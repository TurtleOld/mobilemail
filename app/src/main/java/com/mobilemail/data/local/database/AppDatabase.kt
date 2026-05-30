package com.mobilemail.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import androidx.room.Room
import com.mobilemail.data.local.converter.DateConverter
import com.mobilemail.data.local.converter.FolderRoleConverter
import com.mobilemail.data.local.dao.FolderDao
import com.mobilemail.data.local.dao.MessageDao
import com.mobilemail.data.local.dao.PendingOperationDao
import com.mobilemail.data.local.entity.FolderEntity
import com.mobilemail.data.local.entity.MessageEntity
import com.mobilemail.data.local.entity.PendingOperationEntity

@Database(
    entities = [MessageEntity::class, FolderEntity::class, PendingOperationEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(DateConverter::class, FolderRoleConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun folderDao(): FolderDao
    abstract fun pendingOperationDao(): PendingOperationDao
    
    companion object {
        const val DATABASE_NAME = "mobilemail_db"

        @Volatile
        private var instance: AppDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_operations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        server TEXT NOT NULL,
                        email TEXT NOT NULL,
                        accountId TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        status TEXT NOT NULL,
                        attemptCount INTEGER NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN queryState TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_folderId_accountId_date ON messages(folderId, accountId, date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_accountId_threadId ON messages(accountId, threadId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_accountId_fromEmail ON messages(accountId, fromEmail)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_syncedAt ON messages(syncedAt)")
            }
        }

        internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
