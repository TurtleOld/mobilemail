package com.mobilemail.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "pin_preferences"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_VERSION = "pin_version"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val PIN_VERSION_PBKDF2 = 2
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        const val PIN_LENGTH = 6
        const val MAX_FAILED_ATTEMPTS = 10
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePin(pin: String) {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = hashPinPbkdf2(pin, salt)
        encryptedPrefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putInt(KEY_PIN_VERSION, PIN_VERSION_PBKDF2)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = encryptedPrefs.getString(KEY_PIN_HASH, null) ?: return false
        return when (encryptedPrefs.getInt(KEY_PIN_VERSION, 1)) {
            PIN_VERSION_PBKDF2 -> verifyPbkdf2(pin, storedHash)
            else -> verifyLegacy(pin, storedHash)
        }
    }

    fun isPinEnabled(): Boolean {
        return encryptedPrefs.getString(KEY_PIN_HASH, null) != null
    }

    fun clearPin() {
        encryptedPrefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_VERSION)
            .remove(KEY_BIOMETRIC_ENABLED)
            .remove(KEY_FAILED_ATTEMPTS)
            .apply()
    }

    fun isBiometricEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        encryptedPrefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    fun getFailedAttempts(): Int {
        return encryptedPrefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    fun incrementFailedAttempts(): Int {
        val attempts = getFailedAttempts() + 1
        encryptedPrefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, attempts)
            .apply()
        return attempts
    }

    fun resetFailedAttempts() {
        encryptedPrefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()
    }

    fun hasExceededMaxAttempts(): Boolean {
        return getFailedAttempts() >= MAX_FAILED_ATTEMPTS
    }

    private fun verifyPbkdf2(pin: String, storedHash: String): Boolean {
        val saltBase64 = encryptedPrefs.getString(KEY_PIN_SALT, null) ?: return false
        val salt = runCatching { Base64.decode(saltBase64, Base64.DEFAULT) }.getOrNull() ?: return false
        val inputHash = hashPinPbkdf2(pin, salt)
        return MessageDigest.isEqual(storedHash.toByteArray(), inputHash.toByteArray())
    }

    private fun verifyLegacy(pin: String, storedHash: String): Boolean {
        val inputHash = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val matches = MessageDigest.isEqual(storedHash.toByteArray(), inputHash.toByteArray())
        if (matches) {
            savePin(pin)
        }
        return matches
    }

    private fun hashPinPbkdf2(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
    }
}
