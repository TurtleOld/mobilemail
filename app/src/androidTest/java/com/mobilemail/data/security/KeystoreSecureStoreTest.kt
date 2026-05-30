package com.mobilemail.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KeystoreSecureStoreTest {

    private lateinit var context: Context
    private lateinit var prefsName: String
    private lateinit var keyAlias: String
    private lateinit var store: KeystoreSecureStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefsName = "keystore_store_test_${UUID.randomUUID()}"
        keyAlias = "keystore_alias_test_${UUID.randomUUID()}"
        store = KeystoreSecureStore(
            context = context,
            prefsName = prefsName,
            keyAlias = keyAlias
        )
    }

    @Test
    fun putAndGetString_roundTrip() {
        store.putString("token", "secret-value")

        assertEquals("secret-value", store.getString("token"))
        assertTrue(store.contains("token"))
    }

    @Test
    fun putNull_removesValue() {
        store.putString("token", "secret-value")
        store.putString("token", null)

        assertNull(store.getString("token"))
        assertFalse(store.contains("token"))
    }

    @Test
    fun primitiveAccessors_roundTrip() {
        store.putInt("attempts", 5)
        store.putLong("expiresAt", 123456789L)
        store.putBoolean("enabled", true)

        assertEquals(5, store.getInt("attempts"))
        assertEquals(123456789L, store.getLong("expiresAt"))
        assertEquals(true, store.getBoolean("enabled"))
    }

    @Test
    fun getString_returnsNullOnCorruptedCiphertext() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString("broken", "invalid-payload")
            .apply()

        assertNull(store.getString("broken"))
    }
}
