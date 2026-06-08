package com.mobilemail.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(AppDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrateFrom1To5_addsPendingOperationsFolderQueryStateMessageFtsAndMessageIndexes() {
        helper.createDatabase(TEST_DB, 1).apply {
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
                    syncedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            *AppDatabase.ALL_MIGRATIONS
        ).apply {
            query("PRAGMA table_info(folders)").use { cursor ->
                assertTrue("folders.queryState column must exist", cursorHasColumn(cursor, "queryState"))
            }
            query("PRAGMA table_info(pending_operations)").use { cursor ->
                assertTrue("pending_operations.id column must exist", cursorHasColumn(cursor, "id"))
            }
            query("PRAGMA index_list(messages)").use { cursor ->
                assertTrue(
                    "messages index for folderId/accountId/date must exist",
                    cursorHasValue(cursor, "index_messages_folderId_accountId_date")
                )
                assertTrue(
                    "messages index for accountId/threadId must exist",
                    cursorHasValue(cursor, "index_messages_accountId_threadId")
                )
                assertTrue(
                    "messages index for accountId/folderId/date must exist",
                    cursorHasValue(cursor, "index_messages_accountId_folderId_date")
                )
                assertTrue(
                    "messages index for accountId must exist",
                    cursorHasValue(cursor, "index_messages_accountId")
                )
                assertTrue(
                    "messages index for folderId must exist",
                    cursorHasValue(cursor, "index_messages_folderId")
                )
                assertTrue(
                    "messages index for date must exist",
                    cursorHasValue(cursor, "index_messages_date")
                )
            }
            query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                assertTrue("messages_fts table must exist", cursorHasValue(cursor, "messages_fts"))
            }
        }
    }

    private fun cursorHasColumn(
        cursor: android.database.Cursor,
        columnName: String
    ): Boolean {
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == columnName) {
                return true
            }
        }
        return false
    }

    private fun cursorHasValue(
        cursor: android.database.Cursor,
        value: String
    ): Boolean {
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == value) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val TEST_DB = "app-database-migration-test"
    }
}
