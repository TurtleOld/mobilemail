package com.mobilemail.data.local

import com.mobilemail.data.preferences.SavedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalAccountIdentityTest {

    @Test
    fun `same server and email produce the same key`() {
        assertEquals(
            LocalAccountIdentity.of("https://mail.example.com", "user@example.com"),
            LocalAccountIdentity.of("https://mail.example.com", "user@example.com")
        )
    }

    @Test
    fun `same email on different servers produces different keys`() {
        assertNotEquals(
            LocalAccountIdentity.of("https://mail.example.com", "user@example.com"),
            LocalAccountIdentity.of("https://mail.other.example", "user@example.com")
        )
    }

    @Test
    fun `different emails on one server produce different keys`() {
        assertNotEquals(
            LocalAccountIdentity.of("https://mail.example.com", "first@example.com"),
            LocalAccountIdentity.of("https://mail.example.com", "second@example.com")
        )
    }

    @Test
    fun `legacy owners map an account id that one saved account owns`() {
        val owners = LocalAccountIdentity.legacyOwners(
            listOf(SavedSession("https://mail.example.com", "user@example.com", "server-account-1"))
        )

        assertEquals(
            mapOf("server-account-1" to "https://mail.example.com|user@example.com"),
            owners
        )
    }

    @Test
    fun `legacy owners skip an account id shared by two servers`() {
        val owners = LocalAccountIdentity.legacyOwners(
            listOf(
                SavedSession("https://mail.first.example", "first@example.com", "d"),
                SavedSession("https://mail.second.example", "second@example.com", "d"),
                SavedSession("https://mail.first.example", "unique@example.com", "unique")
            )
        )

        assertEquals(
            mapOf("unique" to "https://mail.first.example|unique@example.com"),
            owners
        )
    }

    @Test
    fun `legacy owners skip an account id shared by two accounts of one server`() {
        val owners = LocalAccountIdentity.legacyOwners(
            listOf(
                SavedSession("https://mail.example.com", "first@example.com", "d"),
                SavedSession("https://mail.example.com", "second@example.com", "d")
            )
        )

        assertEquals(emptyMap<String, String>(), owners)
    }
}
