package com.mobilemail.data.local.database

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDatabaseOpenPreservesDataTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `wrong passphrase keeps the database file and the pending queue`() {
        val databaseFile = temporaryFolder.newFile(DATABASE_FILE_NAME)
        val originalContent = DATABASE_WITH_PENDING_QUEUE.toByteArray()
        databaseFile.writeBytes(originalContent)

        val error = assertThrows(DatabaseOpenException::class.java) {
            AppDatabase.openExistingDatabase(databaseFile) {
                throw IllegalStateException("file is not a database")
            }
        }

        assertTrue(error.isWrongPassphrase)
        assertTrue(databaseFile.exists())
        assertArrayEquals(originalContent, databaseFile.readBytes())
    }

    @Test
    fun `retry succeeds after the key is restored without the file being touched`() {
        val databaseFile = temporaryFolder.newFile(DATABASE_FILE_NAME)
        val originalContent = DATABASE_WITH_PENDING_QUEUE.toByteArray()
        databaseFile.writeBytes(originalContent)
        var keyRestored = false

        assertThrows(DatabaseOpenException::class.java) {
            AppDatabase.openExistingDatabase(databaseFile) {
                check(keyRestored) { "file is not a database" }
            }
        }
        keyRestored = true
        val opened = AppDatabase.openExistingDatabase(databaseFile) { "opened" }

        assertEquals("opened", opened)
        assertArrayEquals(originalContent, databaseFile.readBytes())
    }

    @Test
    fun `other open errors keep the database file and are reported`() {
        val databaseFile = temporaryFolder.newFile(DATABASE_FILE_NAME)
        val originalContent = DATABASE_WITH_PENDING_QUEUE.toByteArray()
        databaseFile.writeBytes(originalContent)

        val error = assertThrows(DatabaseOpenException::class.java) {
            AppDatabase.openExistingDatabase(databaseFile) {
                throw IllegalStateException("no such table: messages")
            }
        }

        assertFalse(error.isWrongPassphrase)
        assertTrue(databaseFile.exists())
        assertArrayEquals(originalContent, databaseFile.readBytes())
    }

    private companion object {
        const val DATABASE_FILE_NAME = "mobilemail_db"

        // The queue lives in the same encrypted file, so keeping the bytes intact keeps it too.
        const val DATABASE_WITH_PENDING_QUEUE =
            "SQLite format 3\u0000 pending_operations: send move"
    }
}
