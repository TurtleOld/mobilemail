package com.mobilemail.data.jmap

import com.mobilemail.data.oauth.OAuthServerMetadata
import com.mobilemail.data.oauth.StoredToken
import com.mobilemail.data.oauth.TokenResponse
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Проверяет, что замена версии черновика не теряет его при частичном успехе Email/set.
 */
class JmapOAuthClientDraftReplacementTest {

    @Test
    fun `saveDraft keeps previous draft when creation of the new version is rejected`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse(server)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(draftsMailboxResponse))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "methodResponses": [
                    ["Email/set", {"notCreated":{"draft":{"type":"overQuota","description":"quota exceeded"}}}, "d1"]
                  ]
                }
                """.trimIndent()
            )
        )
        try {
            val client = jmapClient(server)

            try {
                client.saveDraft(
                    from = "user@example.com",
                    to = listOf("rcpt@example.com"),
                    subject = "Subject",
                    body = "Body",
                    attachments = emptyList(),
                    draftId = "oldDraft",
                    accountId = "acc1"
                )
                fail("Expected rejected creation to fail")
            } catch (expected: Exception) {
                assertTrue(expected.message.orEmpty().contains("quota exceeded"))
            }

            val requestBodies = requestBodies(server)
            assertTrue(requestBodies.any { it.contains("\"create\"") })
            assertFalse(
                "Previous draft must not be destroyed when creation failed",
                requestBodies.any { it.contains("\"destroy\"") }
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `saveDraft destroys previous draft only after the new version is confirmed`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse(server)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(draftsMailboxResponse))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"methodResponses":[["Email/set", {"created":{"draft":{"id":"newDraft"}}}, "d1"]]}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"methodResponses":[["Email/set", {"destroyed":["oldDraft"]}, "d2"]]}"""
            )
        )
        try {
            val client = jmapClient(server)

            val savedId = client.saveDraft(
                from = "user@example.com",
                to = listOf("rcpt@example.com"),
                subject = "Subject",
                body = "Body",
                attachments = emptyList(),
                draftId = "oldDraft",
                accountId = "acc1"
            )

            assertEquals("newDraft", savedId)

            val requestBodies = requestBodies(server)
            val createIndex = requestBodies.indexOfFirst { it.contains("\"create\"") }
            val destroyIndex = requestBodies.indexOfFirst { it.contains("\"destroy\"") }
            assertTrue("Destroy must be a separate request after create", destroyIndex > createIndex)
            assertFalse(requestBodies[createIndex].contains("\"destroy\""))
            assertTrue(requestBodies[destroyIndex].contains("oldDraft"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `saveDraft reports success when the previous draft cannot be destroyed`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse(server)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(draftsMailboxResponse))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"methodResponses":[["Email/set", {"created":{"draft":{"id":"newDraft"}}}, "d1"]]}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "methodResponses": [
                    ["Email/set", {"notDestroyed":{"oldDraft":{"type":"forbidden","description":"denied"}}}, "d2"]
                  ]
                }
                """.trimIndent()
            )
        )
        try {
            val client = jmapClient(server)

            val savedId = client.saveDraft(
                from = "user@example.com",
                to = listOf("rcpt@example.com"),
                subject = "Subject",
                body = "Body",
                attachments = emptyList(),
                draftId = "oldDraft",
                accountId = "acc1"
            )

            assertEquals("newDraft", savedId)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `saveDraft reports success when the destroy request itself fails`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse(server)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(draftsMailboxResponse))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"methodResponses":[["Email/set", {"created":{"draft":{"id":"newDraft"}}}, "d1"]]}"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"server_error"}"""))
        try {
            val client = jmapClient(server)

            val savedId = client.saveDraft(
                from = "user@example.com",
                to = listOf("rcpt@example.com"),
                subject = "Subject",
                body = "Body",
                attachments = emptyList(),
                draftId = "oldDraft",
                accountId = "acc1"
            )

            assertEquals("newDraft", savedId)
        } finally {
            server.shutdown()
        }
    }

    private fun requestBodies(server: MockWebServer): List<String> =
        (0 until server.requestCount).map { server.takeRequest().body.readUtf8() }

    private fun jmapClient(server: MockWebServer): JmapOAuthClient = JmapOAuthClient(
        baseUrl = server.url("/").toString(),
        email = "user@example.com",
        accountId = "acc1",
        tokenStoreAccess = DraftTestTokenStore(),
        metadata = OAuthServerMetadata(
            issuer = "test",
            deviceAuthorizationEndpoint = server.url("/device").toString(),
            tokenEndpoint = server.url("/token").toString(),
            authorizationEndpoint = null,
            registrationEndpoint = null,
            introspectionEndpoint = null,
            revocationEndpoint = null,
            grantTypesSupported = emptyList(),
            responseTypesSupported = null,
            scopesSupported = null
        ),
        clientId = "mobilemail-test"
    )

    private fun sessionResponse(server: MockWebServer): String {
        return """
            {
              "apiUrl": "${server.url("/jmap/api")}",
              "downloadUrl": "${server.url("/jmap/download")}",
              "uploadUrl": "${server.url("/jmap/upload")}",
              "accounts": {
                "acc1": {
                  "name": "Personal"
                }
              },
              "primaryAccounts": {
                "mail": "acc1"
              }
            }
        """.trimIndent()
    }

    private companion object {
        const val draftsMailboxResponse =
            """{"methodResponses":[["Mailbox/get", {"list":[{"id":"drafts","name":"Drafts","role":"drafts"}]}, "0"]]}"""
    }
}

private class DraftTestTokenStore : TokenStoreAccess {
    private val token = StoredToken(
        accessToken = "access",
        tokenType = "Bearer",
        expiresAt = System.currentTimeMillis() + 60_000,
        refreshToken = "refresh"
    )

    override fun getTokens(server: String, email: String): StoredToken = token

    override fun saveTokens(server: String, email: String, tokenResponse: TokenResponse) = Unit

    override fun clearTokens(server: String, email: String) = Unit
}
