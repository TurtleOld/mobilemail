package com.mobilemail.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreSecureStore(
    context: Context,
    prefsName: String,
    private val keyAlias: String
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun putString(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        prefs.edit().putString(key, encrypt(value)).apply()
    }

    fun getString(key: String): String? {
        val encrypted = prefs.getString(key, null) ?: return null
        return decrypt(encrypted)
    }

    fun putLong(key: String, value: Long?) {
        putString(key, value?.toString())
    }

    fun getLong(key: String): Long? {
        return getString(key)?.toLongOrNull()
    }

    fun putInt(key: String, value: Int?) {
        putString(key, value?.toString())
    }

    fun getInt(key: String): Int? {
        return getString(key)?.toIntOrNull()
    }

    fun putBoolean(key: String, value: Boolean?) {
        putString(key, value?.toString())
    }

    fun getBoolean(key: String): Boolean? {
        return getString(key)?.toBooleanStrictOrNull()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    fun remove(vararg keys: String) {
        val editor = prefs.edit()
        keys.forEach(editor::remove)
        editor.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val payloadBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$ivBase64:$payloadBase64"
    }

    private fun decrypt(encryptedValue: String): String? {
        val parts = encryptedValue.split(":", limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.DEFAULT)
            val payload = Base64.decode(parts[1], Base64.DEFAULT)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            String(cipher.doFinal(payload), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
