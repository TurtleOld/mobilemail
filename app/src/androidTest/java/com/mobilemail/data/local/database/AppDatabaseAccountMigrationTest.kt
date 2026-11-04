package com.mobilemail.data.local.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Applies [AppDatabase.MIGRATION_5_6] to a version 5 database.
 *
 * [androidx.room.testing.MigrationTestHelper] cannot be used here: the instrumented test classpath
 * resolves Kotlin serialization versions that fail to read the exported schema bundles.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseAccountMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `migration keeps cache of one account and the whole queue`() {
        createVersion5Database()

        openDatabase(version = 6, callback = MigratingCallback(LEGACY_ACCOUNT_OWNERS)).use { helper ->
            val database = helper.writableDatabase

            database.query("SELECT id, localAccountId FROM messages").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("m-unique", cursor.getString(0))
                assertEquals(UNIQUE_ACCOUNT_KEY, cursor.getString(1))
            }
            database.query("SELECT localAccountId FROM folders").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(UNIQUE_ACCOUNT_KEY, cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM pending_operations").use { cursor ->
                cursor.moveToFirst()
                assertEquals(3, cursor.getInt(0))
            }
            database.query("SELECT localAccountId, messageId FROM messages_fts").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(UNIQUE_ACCOUNT_KEY, cursor.getString(0))
                assertEquals("m-unique", cursor.getString(1))
            }
            assertPrimaryKey(database, "m-unique")
        }
    }

    @Test
    fun `migration recreates fts trigger for local account id`() {
        createVersion5Database()

        openDatabase(version = 6, callback = MigratingCallback(LEGACY_ACCOUNT_OWNERS)).use { helper ->
            val database = helper.writableDatabase
            database.execSQL(
                """
                INSERT INTO messages(
                    localAccountId, id, threadId, folderId, accountId, fromName, fromEmail,
                    subject, snippet, date, isUnread, isStarred, isImportant, hasAttachments,
                    size, textBody, htmlBody, syncedAt
                ) VALUES(
                    '$UNIQUE_ACCOUNT_KEY', 'm-new', 'thread-1', 'inbox', 'unique', 'Sender',
                    'sender@example.com', 'new subject', 'snippet', 1700000000000, 1, 0, 0, 0,
                    1024, 'body', NULL, 1700000000000
                )
                """.trimIndent()
            )

            database.query("SELECT COUNT(*) FROM messages_fts WHERE messageId = 'm-new'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `migration drops cache rows that cannot be assigned to one account`() {
        createVersion5Database()

        openDatabase(version = 6, callback = MigratingCallback(LEGACY_ACCOUNT_OWNERS)).use { helper ->
            helper.writableDatabase.query(
                "SELECT COUNT(*) FROM messages WHERE id IN ('m-ambiguous', 'm-orphan')"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private fun assertPrimaryKey(database: SupportSQLiteDatabase, messageId: String) {
        database.query("PRAGMA table_info(messages)").use { cursor ->
            val pkPositions = mutableMapOf<String, Int>()
            while (cursor.moveToNext()) {
                pkPositions[cursor.getString(cursor.getColumnIndexOrThrow("name"))] =
                    cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
            }
            assertEquals(1, pkPositions["localAccountId"])
            assertEquals(2, pkPositions["id"])
        }
        database.query("SELECT COUNT(*) FROM messages WHERE id = '$messageId'").use { cursor ->
            cursor.moveToFirst()
            assertTrue(cursor.getInt(0) > 0)
        }
    }

    private fun createVersion5Database() {
        openDatabase(version = 5, callback = LegacyCallback()).use { helper ->
            helper.writableDatabase.apply {
                createLegacyTables()
                insertMessage(id = "m-unique", accountId = "unique")
                insertMessage(id = "m-ambiguous", accountId = "shared")
                insertMessage(id = "m-orphan", accountId = "unknown")
                insertFolder(id = "f-unique", accountId = "unique")
                insertFolder(id = "f-ambiguous", accountId = "shared")
                insertOperation(
                    server = "https://mail.unique.example",
                    email = "unique@example.com",
                    accountId = "unique"
                )
                insertOperation(
                    server = "https://mail.first.example",
                    email = "first@example.com",
                    accountId = "shared"
                )
                insertOperation(
                    server = "https://mail.second.example",
                    email = "second@example.com",
                    accountId = "shared"
                )
            }
        }
    }

    private fun openDatabase(
        version: Int,
        callback: SupportSQLiteOpenHelper.Callback
    ): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private class LegacyCallback : SupportSQLiteOpenHelper.Callback(5) {
        override fun onCreate(db: SupportSQLiteDatabase) = db.createLegacyTables()

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private class MigratingCallback(
        private val legacyAccountOwners: Map<String, String>
    ) : SupportSQLiteOpenHelper.Callback(6) {
        override fun onCreate(db: SupportSQLiteDatabase) = error("database must be created at version 5")

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion == 5 && newVersion == 6) {
                AppDatabase.migration5To6 { legacyAccountOwners }.migrate(db)
            }
        }
    }

    private fun SupportSQLiteDatabase.insertMessage(id: String, accountId: String) {
        execSQL(
            """
            INSERT INTO messages(
                id, threadId, folderId, accountId, fromName, fromEmail, subject, snippet,
                date, isUnread, isStarred, isImportant, hasAttachments, size, textBody,
                htmlBody, syncedAt
            ) VALUES(
                '$id', 'thread-1', 'inbox', '$accountId', 'Sender', 'sender@example.com',
                'subject $id', 'snippet', 1700000000000, 1, 0, 0, 0, 1024, 'body', NULL,
                1700000000000
            )
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.insertFolder(id: String, accountId: String) {
        execSQL(
            """
            INSERT INTO folders(id, accountId, name, role, unreadCount, queryState, syncedAt)
            VALUES('$id', '$accountId', 'Inbox', 'INBOX', 1, NULL, 1700000000000)
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.insertOperation(server: String, email: String, accountId: String) {
        execSQL(
            """
            INSERT INTO pending_operations(
                type, server, email, accountId, payload, status, attemptCount, lastError,
                createdAt, updatedAt
            ) VALUES(
                'mark_read', '$server', '$email', '$accountId', '{}', 'pending', 0, NULL,
                1700000000000, 1700000000000
            )
            """.trimIndent()
        )
    }

    companion object {
        private const val TEST_DB = "app-database-account-migration-test"
        private const val UNIQUE_ACCOUNT_KEY = "https://mail.unique.example|unique@example.com"
        private val LEGACY_ACCOUNT_OWNERS = mapOf("unique" to UNIQUE_ACCOUNT_KEY)
    }
}

private fun SupportSQLiteDatabase.createLegacyTables() {
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS messages (
            id TEXT NOT NULL PRIMARY KEY,
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
            syncedAt INTEGER NOT NULL
        )
        """.trimIndent()
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS folders (
            id TEXT NOT NULL PRIMARY KEY,
            accountId TEXT NOT NULL,
            name TEXT NOT NULL,
            role TEXT NOT NULL,
            unreadCount INTEGER NOT NULL,
            queryState TEXT,
            syncedAt INTEGER NOT NULL
        )
        """.trimIndent()
    )
    execSQL(
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
