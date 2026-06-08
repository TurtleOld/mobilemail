package com.mobilemail.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobilemail.data.local.converter.DateConverter
import com.mobilemail.data.local.converter.FolderRoleConverter
import com.mobilemail.data.local.dao.FolderDao
import com.mobilemail.data.local.dao.MessageDao
import com.mobilemail.data.local.dao.PendingOperationDao
import com.mobilemail.data.local.entity.FolderEntity
import com.mobilemail.data.local.entity.MessageEntity
import com.mobilemail.data.local.entity.MessageFtsEntity
import com.mobilemail.data.local.entity.PendingOperationEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [MessageEntity::class, MessageFtsEntity::class, FolderEntity::class, PendingOperationEntity::class],
    version = 4,
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

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createMessageFtsTable(db)
                rebuildMessageFtsIndex(db)
                createMessageFtsTriggers(db)
            }
        }

        internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildEncryptedDatabase(context.applicationContext)
                    .also { instance = it }
            }
        }

        private fun buildEncryptedDatabase(context: Context): AppDatabase {
            return runCatching {
                createEncryptedDatabase(context).also { it.openHelper.writableDatabase }
            }.getOrElse {
                context.deleteDatabase(DATABASE_NAME)
                createEncryptedDatabase(context).also { it.openHelper.writableDatabase }
            }
        }

        private fun createEncryptedDatabase(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabasePassphraseProvider(context).getOrCreatePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createMessageFtsTriggers(db)
                    }
                })
                .build()
        }

        private fun createMessageFtsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING FTS4(
                    messageId TEXT NOT NULL,
                    accountId TEXT NOT NULL,
                    folderId TEXT NOT NULL,
                    fromName TEXT,
                    fromEmail TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    snippet TEXT NOT NULL,
                    textBody TEXT,
                    htmlBody TEXT,
                    tokenize=unicode61
                )
                """.trimIndent()
            )
        }

        private fun rebuildMessageFtsIndex(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM messages_fts")
            db.execSQL(
                """
                INSERT INTO messages_fts(
                    messageId, accountId, folderId, fromName, fromEmail,
                    subject, snippet, textBody, htmlBody
                )
                SELECT
                    id, accountId, folderId, fromName, fromEmail,
                    subject, snippet, textBody, htmlBody
                FROM messages
                """.trimIndent()
            )
        }

        private fun createMessageFtsTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
                    DELETE FROM messages_fts WHERE messageId = new.id;
                    INSERT INTO messages_fts(
                        messageId, accountId, folderId, fromName, fromEmail,
                        subject, snippet, textBody, htmlBody
                    )
                    VALUES(
                        new.id, new.accountId, new.folderId, new.fromName, new.fromEmail,
                        new.subject, new.snippet, new.textBody, new.htmlBody
                    );
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE ON messages BEGIN
                    DELETE FROM messages_fts WHERE messageId = old.id;
                    INSERT INTO messages_fts(
                        messageId, accountId, folderId, fromName, fromEmail,
                        subject, snippet, textBody, htmlBody
                    )
                    VALUES(
                        new.id, new.accountId, new.folderId, new.fromName, new.fromEmail,
                        new.subject, new.snippet, new.textBody, new.htmlBody
                    );
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
                    DELETE FROM messages_fts WHERE messageId = old.id;
                END
                """.trimIndent()
            )
        }
    }
}
