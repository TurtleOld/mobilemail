package com.mobilemail.data.security

import android.content.Context
import android.util.Base64
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
        private const val KEY_LAST_FAILED_ATTEMPT_AT_MILLIS = "last_failed_attempt_at_millis"
        private const val PIN_VERSION_PBKDF2 = 2
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        const val PIN_LENGTH = 6
        const val MAX_FAILED_ATTEMPTS = 10
    }

    private val secureStore = KeystoreSecureStore(
        context = context,
        prefsName = "${PREFS_NAME}_v2",
        keyAlias = "mobilemail_pin_key"
    )

    fun savePin(pin: String) {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = hashPinPbkdf2(pin, salt)
        secureStore.putString(KEY_PIN_HASH, hash)
        secureStore.putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
        secureStore.putInt(KEY_PIN_VERSION, PIN_VERSION_PBKDF2)
        secureStore.putInt(KEY_FAILED_ATTEMPTS, 0)
        secureStore.remove(KEY_LAST_FAILED_ATTEMPT_AT_MILLIS)
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = secureStore.getString(KEY_PIN_HASH) ?: return false
        return when (secureStore.getInt(KEY_PIN_VERSION) ?: 1) {
            PIN_VERSION_PBKDF2 -> verifyPbkdf2(pin, storedHash)
            else -> verifyLegacy(pin, storedHash)
        }
    }

    fun isPinEnabled(): Boolean {
        return secureStore.getString(KEY_PIN_HASH) != null
    }

    fun clearPin() {
        secureStore.remove(
            KEY_PIN_HASH,
            KEY_PIN_SALT,
            KEY_PIN_VERSION,
            KEY_BIOMETRIC_ENABLED,
            KEY_FAILED_ATTEMPTS,
            KEY_LAST_FAILED_ATTEMPT_AT_MILLIS
        )
    }

    fun isBiometricEnabled(): Boolean {
        return secureStore.getBoolean(KEY_BIOMETRIC_ENABLED) ?: false
    }

    fun setBiometricEnabled(enabled: Boolean) {
        secureStore.putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
    }

    fun getFailedAttempts(): Int {
        return secureStore.getInt(KEY_FAILED_ATTEMPTS) ?: 0
    }

    fun incrementFailedAttempts(nowMillis: Long = System.currentTimeMillis()): Int {
        val attempts = getFailedAttempts() + 1
        secureStore.putInt(KEY_FAILED_ATTEMPTS, attempts)
        secureStore.putLong(KEY_LAST_FAILED_ATTEMPT_AT_MILLIS, nowMillis)
        return attempts
    }

    fun resetFailedAttempts() {
        secureStore.putInt(KEY_FAILED_ATTEMPTS, 0)
        secureStore.remove(KEY_LAST_FAILED_ATTEMPT_AT_MILLIS)
    }

    fun hasExceededMaxAttempts(): Boolean {
        return getFailedAttempts() >= MAX_FAILED_ATTEMPTS
    }

    fun getRemainingLockoutMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return PinLockoutPolicy.remainingDelayMillis(
            attempts = getFailedAttempts(),
            lastFailedAttemptAtMillis = secureStore.getLong(KEY_LAST_FAILED_ATTEMPT_AT_MILLIS),
            nowMillis = nowMillis,
        )
    }

    private fun verifyPbkdf2(pin: String, storedHash: String): Boolean {
        val saltBase64 = secureStore.getString(KEY_PIN_SALT)
        val salt = saltBase64?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
        // Always hash to avoid timing leak on missing salt; result discarded if salt absent
        val inputHash = if (salt != null) hashPinPbkdf2(pin, salt) else hashPinPbkdf2(pin, ByteArray(16))
        if (salt == null) return false
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
