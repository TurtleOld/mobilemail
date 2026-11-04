package com.mobilemail.data.local.database

import java.io.File

/**
 * Signals that the encrypted database exists but could not be opened.
 *
 * The database file also holds the pending operations queue, so callers must neither delete nor
 * recreate it: the user has to be able to restore access to the encryption key and retry.
 *
 * @param databaseFile Database file that could not be opened and was left untouched.
 * @param isWrongPassphrase True when the file was rejected as "file is not a database", which
 * usually means the encryption key no longer matches the stored file.
 * @param cause Failure that prevented the database from opening.
 */
class DatabaseOpenException(
    val databaseFile: File,
    val isWrongPassphrase: Boolean,
    cause: Throwable
) : Exception(
    "The encrypted database was not opened and was preserved at ${databaseFile.path}",
    cause
)
