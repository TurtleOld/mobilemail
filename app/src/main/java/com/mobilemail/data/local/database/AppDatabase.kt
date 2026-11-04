package com.mobilemail.data.local.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobilemail.data.local.LocalAccountIdentity
import com.mobilemail.data.local.converter.DateConverter
import com.mobilemail.data.local.converter.FolderRoleConverter
import com.mobilemail.data.local.dao.FolderDao
import com.mobilemail.data.local.dao.MessageDao
import com.mobilemail.data.local.dao.PendingOperationDao
import com.mobilemail.data.local.entity.FolderEntity
import com.mobilemail.data.local.entity.MessageEntity
import com.mobilemail.data.local.entity.MessageFtsEntity
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.data.preferences.PreferencesManager
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(
    entities = [MessageEntity::class, MessageFtsEntity::class, FolderEntity::class, PendingOperationEntity::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(DateConverter::class, FolderRoleConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun folderDao(): FolderDao
    abstract fun pendingOperationDao(): PendingOperationDao
    
    companion object {
        const val DATABASE_NAME = "mobilemail_db"
        private const val WRONG_PASSPHRASE_MESSAGE = "file is not a database"

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
                createLegacyMessageFtsTable(db)
                rebuildLegacyMessageFtsIndex(db)
                createLegacyMessageFtsTriggers(db)
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createMessagePerformanceIndexes(db)
            }
        }

        /**
         * Moves cached mail from server account ids to [MessageEntity.localAccountId] keys.
         *
         * The [legacyAccountOwners] lookup returns server account ids that exactly one saved
         * account owns.
         * Cache rows of those ids are kept; rows of ids shared by several accounts, or unknown to
         * this device, are dropped instead of being assigned to an arbitrary account. The queue
         * already stores the server and email, so it is left untouched.
         *
         * @param legacyAccountOwners Lazy lookup of server account id to local account key.
         */
        internal fun migration5To6(legacyAccountOwners: () -> Map<String, String>): Migration =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    createLegacyAccountIdentityMap(db, legacyAccountOwners())
                    migrateMessagesToLocalAccountId(db)
                    migrateFoldersToLocalAccountId(db)
                    recreateMessageFtsForLocalAccountId(db)
                    db.execSQL("DROP TABLE IF EXISTS legacy_account_identity")
                }
            }

        internal fun migrations(legacyAccountOwners: () -> Map<String, String>): Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            migration5To6(legacyAccountOwners)
        )

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildEncryptedDatabase(context.applicationContext)
                    .also { instance = it }
            }
        }

        private fun buildEncryptedDatabase(context: Context): AppDatabase {
            val databaseFile = context.getDatabasePath(DATABASE_NAME)
            return openExistingDatabase(databaseFile) {
                createEncryptedDatabase(context).also { it.openHelper.writableDatabase }
            }
        }

        private fun readLegacyAccountOwners(context: Context): Map<String, String> {
            return runCatching {
                runBlocking {
                    LocalAccountIdentity.legacyOwners(PreferencesManager(context).getSavedAccounts())
                }
            }.getOrElse { error ->
                Log.w("AppDatabase", "Cannot read saved accounts for cache migration", error)
                emptyMap()
            }
        }

        /**
         * Opens the database without deleting it when opening fails.
         *
         * A wrong passphrase is reported as "file is not a database". Deleting the file in that
         * case would also destroy the pending operations queue stored in the same database, so the
         * existing file is always preserved and the failure is surfaced to the caller instead.
         *
         * @param databaseFile Database file that must survive any open failure.
         * @param open Opens the database and verifies that it is usable.
         * @return Opened database returned by [open].
         * @throws DatabaseOpenException When [databaseFile] cannot be opened. The file is untouched.
         */
        internal fun <T> openExistingDatabase(databaseFile: File, open: () -> T): T {
            return try {
                open()
            } catch (error: Throwable) {
                throw DatabaseOpenException(
                    databaseFile = databaseFile,
                    isWrongPassphrase = isWrongPassphraseError(error),
                    cause = error
                )
            }
        }

        /**
         * Checks whether [error] was caused by opening the database with a wrong passphrase.
         *
         * SQLCipher reports a wrong key as "file is not a database". Any other error, such as a
         * failed migration, must not lead to the database being deleted.
         *
         * @param error Failure thrown while opening the database.
         * @return True when the error means the database was encrypted with another key.
         */
        internal fun isWrongPassphraseError(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                if (current.message?.contains(WRONG_PASSPHRASE_MESSAGE, ignoreCase = true) == true) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        @Suppress("SpreadOperator")
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
                .addMigrations(*migrations { readLegacyAccountOwners(context) })
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createMessageFtsTriggers(db)
                    }
                })
                .build()
        }

        private fun createLegacyAccountIdentityMap(
            db: SupportSQLiteDatabase,
            legacyAccountOwners: Map<String, String>
        ) {
            db.execSQL(
                """
                CREATE TEMP TABLE IF NOT EXISTS legacy_account_identity (
                    accountId TEXT NOT NULL PRIMARY KEY,
                    localAccountId TEXT NOT NULL
                )
                """.trimIndent()
            )
            legacyAccountOwners.forEach { (accountId, localAccountId) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO legacy_account_identity(accountId, localAccountId) VALUES(?, ?)",
                    arrayOf(accountId, localAccountId)
                )
            }
        }

        private fun migrateMessagesToLocalAccountId(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS messages_new (
                    localAccountId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    threadId TEXT NOT NULL,
                    folderId TEXT NOT NULL,
                    accountId TEXT NOT NULL,
                    fromName TEXT,
                    fromEmail TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    snippet TEXT NOT NULL,
                    date INTEGER NOT NULL,
                    isUnread INTEGER NOT NULL,
                    isStarred INTEGER NOT NULL,
                    isImportant INTEGER NOT NULL,
                    hasAttachments INTEGER NOT NULL,
                    size INTEGER NOT NULL,
                    textBody TEXT,
                    htmlBody TEXT,
                    syncedAt INTEGER NOT NULL,
                    PRIMARY KEY(localAccountId, id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO messages_new(
                    localAccountId, id, threadId, folderId, accountId, fromName, fromEmail,
                    subject, snippet, date, isUnread, isStarred, isImportant, hasAttachments,
                    size, textBody, htmlBody, syncedAt
                )
                SELECT
                    identity.localAccountId, messages.id, messages.threadId, messages.folderId,
                    messages.accountId, messages.fromName, messages.fromEmail, messages.subject,
                    messages.snippet, messages.date, messages.isUnread, messages.isStarred,
                    messages.isImportant, messages.hasAttachments, messages.size,
                    messages.textBody, messages.htmlBody, messages.syncedAt
                FROM messages
                JOIN legacy_account_identity AS identity ON identity.accountId = messages.accountId
                """.trimIndent()
            )
            db.execSQL("DROP TABLE messages")
            db.execSQL("ALTER TABLE messages_new RENAME TO messages")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_localAccountId_folderId_date ON messages(localAccountId, folderId, date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_localAccountId ON messages(localAccountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_folderId ON messages(folderId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_date ON messages(date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_localAccountId_threadId ON messages(localAccountId, threadId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_localAccountId_fromEmail ON messages(localAccountId, fromEmail)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_syncedAt ON messages(syncedAt)")
        }

        private fun migrateFoldersToLocalAccountId(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS folders_new (
                    localAccountId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    accountId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    role TEXT NOT NULL,
                    unreadCount INTEGER NOT NULL,
                    queryState TEXT,
                    syncedAt INTEGER NOT NULL,
                    PRIMARY KEY(localAccountId, id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO folders_new(
                    localAccountId, id, accountId, name, role, unreadCount, queryState, syncedAt
                )
                SELECT
                    identity.localAccountId, folders.id, folders.accountId, folders.name,
                    folders.role, folders.unreadCount, folders.queryState, folders.syncedAt
                FROM folders
                JOIN legacy_account_identity AS identity ON identity.accountId = folders.accountId
                """.trimIndent()
            )
            db.execSQL("DROP TABLE folders")
            db.execSQL("ALTER TABLE folders_new RENAME TO folders")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_localAccountId ON folders(localAccountId)")
        }

        private fun recreateMessageFtsForLocalAccountId(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TRIGGER IF EXISTS messages_ai")
            db.execSQL("DROP TRIGGER IF EXISTS messages_au")
            db.execSQL("DROP TRIGGER IF EXISTS messages_ad")
            db.execSQL("DROP TABLE IF EXISTS messages_fts")
            createMessageFtsTable(db)
            rebuildMessageFtsIndex(db)
            createMessageFtsTriggers(db)
        }

        private fun createLegacyMessageFtsTable(db: SupportSQLiteDatabase) {
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

        private fun createMessageFtsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING FTS4(
                    messageId TEXT NOT NULL,
                    localAccountId TEXT NOT NULL,
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

        private fun createMessagePerformanceIndexes(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_accountId_folderId_date ON messages(accountId, folderId, date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_accountId ON messages(accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_folderId ON messages(folderId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_date ON messages(date)")
        }

        private fun rebuildLegacyMessageFtsIndex(db: SupportSQLiteDatabase) {
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

        private fun rebuildMessageFtsIndex(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM messages_fts")
            db.execSQL(
                """
                INSERT INTO messages_fts(
                    messageId, localAccountId, accountId, folderId, fromName, fromEmail,
                    subject, snippet, textBody, htmlBody
                )
                SELECT
                    id, localAccountId, accountId, folderId, fromName, fromEmail,
                    subject, snippet, textBody, htmlBody
                FROM messages
                """.trimIndent()
            )
        }

        private fun createLegacyMessageFtsTriggers(db: SupportSQLiteDatabase) {
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

        internal fun createMessageFtsTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
                    DELETE FROM messages_fts
                    WHERE messageId = new.id AND localAccountId = new.localAccountId;
                    INSERT INTO messages_fts(
                        messageId, localAccountId, accountId, folderId, fromName, fromEmail,
                        subject, snippet, textBody, htmlBody
                    )
                    VALUES(
                        new.id, new.localAccountId, new.accountId, new.folderId, new.fromName,
                        new.fromEmail, new.subject, new.snippet, new.textBody, new.htmlBody
                    );
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE ON messages BEGIN
                    DELETE FROM messages_fts
                    WHERE messageId = old.id AND localAccountId = old.localAccountId;
                    INSERT INTO messages_fts(
                        messageId, localAccountId, accountId, folderId, fromName, fromEmail,
                        subject, snippet, textBody, htmlBody
                    )
                    VALUES(
                        new.id, new.localAccountId, new.accountId, new.folderId, new.fromName,
                        new.fromEmail, new.subject, new.snippet, new.textBody, new.htmlBody
                    );
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
                    DELETE FROM messages_fts
                    WHERE messageId = old.id AND localAccountId = old.localAccountId;
                END
                """.trimIndent()
            )
        }
    }
}
