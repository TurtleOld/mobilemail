package com.mobilemail.data.local.database

import android.content.Context
import android.util.Base64
import com.mobilemail.data.security.KeystoreSecureStore
import java.security.SecureRandom

class DatabasePassphraseProvider(context: Context) {
    private val secureStore = KeystoreSecureStore(
        context = context,
        prefsName = "database_passphrase",
        keyAlias = "mobilemail_database_key"
    )

    fun getOrCreatePassphrase(): ByteArray {
        val existing = secureStore.getString(PASSPHRASE_KEY)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        // An undecryptable stored key must not be replaced: generating a new passphrase would
        // make the existing encrypted database unrecoverable once the original key is restored.
        check(!secureStore.contains(PASSPHRASE_KEY)) {
            "Stored database passphrase exists but cannot be decrypted"
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(passphrase)
        secureStore.putString(PASSPHRASE_KEY, Base64.encodeToString(passphrase, Base64.NO_WRAP))
        return passphrase
    }

    private companion object {
        const val PASSPHRASE_KEY = "room_sqlcipher_passphrase"
        const val PASSPHRASE_BYTES = 32
    }
}
