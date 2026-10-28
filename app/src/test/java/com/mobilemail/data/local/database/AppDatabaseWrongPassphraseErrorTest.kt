package com.mobilemail.data.local.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseWrongPassphraseErrorTest {

    @Test
    fun `wrong passphrase error is recognized`() {
        assertTrue(AppDatabase.isWrongPassphraseError(RuntimeException("file is not a database")))
    }

    @Test
    fun `wrapped wrong passphrase error is recognized`() {
        val error = IllegalStateException("failed to open database", RuntimeException("file is not a database"))

        assertTrue(AppDatabase.isWrongPassphraseError(error))
    }

    @Test
    fun `other database errors are not treated as wrong passphrase`() {
        assertFalse(AppDatabase.isWrongPassphraseError(RuntimeException("no such table: messages")))
    }
}
