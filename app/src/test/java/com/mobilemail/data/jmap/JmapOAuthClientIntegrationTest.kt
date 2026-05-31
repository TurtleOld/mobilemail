package com.mobilemail.data.jmap

import com.mobilemail.data.oauth.OAuthServerMetadata
import com.mobilemail.data.oauth.StoredToken
import com.mobilemail.data.oauth.TokenResponse
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JmapOAuthClientIntegrationTest {

    @Test
    fun `getSession refreshes token and retries after unauthorized`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "access_token": "new_access",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "refresh_token": "new_refresh"
                }
                """.trimIndent()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse(server)))
        try {
            val tokenAccess = FakeTokenStoreAccess(
                StoredToken(
                    accessToken = "old_access",
                    tokenType = "Bearer",
                    expiresAt = System.currentTimeMillis() + 60_000,
                    refreshToken = "old_refresh"
                )
            )

            val client = JmapOAuthClient(
                baseUrl = server.url("/").toString(),
                email = "user@example.com",
                accountId = "acc1",
                tokenStoreAccess = tokenAccess,
                metadata = metadata(server),
                clientId = "mobilemail-test"
            )

            val session = client.getSession()

            assertEquals(server.url("/jmap/api").toString(), session.apiUrl)
            assertEquals("new_access", tokenAccess.currentToken?.accessToken)
            assertEquals(1, tokenAccess.savedTokens.size)

            val firstRequest = server.takeRequest()
            assertEquals("/.well-known/jmap", firstRequest.path)
            assertEquals("Bearer old_access", firstRequest.getHeader("Authorization"))

            val refreshRequest = server.takeRequest()
            assertEquals("/token", refreshRequest.path)
            assertTrue(refreshRequest.body.readUtf8().contains("refresh_token=old_refresh"))

            val retryRequest = server.takeRequest()
            assertEquals("/.well-known/jmap", retryRequest.path)
            assertEquals("Bearer new_access", retryRequest.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `getSession throws when retried request still fails after refresh`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "access_token": "new_access",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "refresh_token": "new_refresh"
                }
                """.trimIndent()
            )
        )
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"server_error"}"""))
        try {
            val tokenAccess = FakeTokenStoreAccess(
                StoredToken(
                    accessToken = "old_access",
                    tokenType = "Bearer",
                    expiresAt = System.currentTimeMillis() + 60_000,
                    refreshToken = "old_refresh"
                )
            )

            val client = JmapOAuthClient(
                baseUrl = server.url("/").toString(),
                email = "user@example.com",
                accountId = "acc1",
                tokenStoreAccess = tokenAccess,
                metadata = metadata(server),
                clientId = "mobilemail-test"
            )

            try {
                client.getSession()
                fail("Expected exception when retry request fails")
            } catch (error: Exception) {
                assertTrue(error.message.orEmpty().contains("после refresh"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `getMailboxes refreshes token and retries JMAP api call`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse(server)))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "access_token": "new_access",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "refresh_token": "new_refresh"
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "methodResponses": [
                    ["Mailbox/get", {"list":[{"id":"inbox","name":"Inbox"}]}, "0"]
                  ]
                }
                """.trimIndent()
            )
        )
        try {
            val tokenAccess = FakeTokenStoreAccess(
                StoredToken(
                    accessToken = "old_access",
                    tokenType = "Bearer",
                    expiresAt = System.currentTimeMillis() + 60_000,
                    refreshToken = "old_refresh"
                )
            )
            val client = JmapOAuthClient(
                baseUrl = server.url("/").toString(),
                email = "user@example.com",
                accountId = "acc1",
                tokenStoreAccess = tokenAccess,
                metadata = metadata(server),
                clientId = "mobilemail-test"
            )

            val mailboxes = client.getMailboxes("acc1")

            assertEquals(1, mailboxes.size)
            assertEquals("inbox", mailboxes.first().id)
            assertEquals("Inbox", mailboxes.first().name)
            assertEquals("new_access", tokenAccess.currentToken?.accessToken)

            val sessionRequest = server.takeRequest()
            assertEquals("/.well-known/jmap", sessionRequest.path)
            assertEquals("Bearer old_access", sessionRequest.getHeader("Authorization"))

            val firstApiRequest = server.takeRequest()
            assertEquals("/jmap/api", firstApiRequest.path)
            assertEquals("Bearer old_access", firstApiRequest.getHeader("Authorization"))

            val refreshRequest = server.takeRequest()
            assertEquals("/token", refreshRequest.path)
            assertTrue(refreshRequest.body.readUtf8().contains("refresh_token=old_refresh"))

            val retryApiRequest = server.takeRequest()
            assertEquals("/jmap/api", retryApiRequest.path)
            assertEquals("Bearer new_access", retryApiRequest.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `getMailboxes throws on JMAP error envelope`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse(server)))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "methodResponses": [
                    ["error", {"type":"serverFail","description":"temporary issue"}, "0"]
                  ]
                }
                """.trimIndent()
            )
        )
        try {
            val tokenAccess = FakeTokenStoreAccess(
                StoredToken(
                    accessToken = "old_access",
                    tokenType = "Bearer",
                    expiresAt = System.currentTimeMillis() + 60_000,
                    refreshToken = "old_refresh"
                )
            )
            val client = JmapOAuthClient(
                baseUrl = server.url("/").toString(),
                email = "user@example.com",
                accountId = "acc1",
                tokenStoreAccess = tokenAccess,
                metadata = metadata(server),
                clientId = "mobilemail-test"
            )

            try {
                client.getMailboxes("acc1")
                fail("Expected JMAP method error")
            } catch (error: Exception) {
                assertTrue(error.message.orEmpty().contains("JMAP method error"))
                assertTrue(error.message.orEmpty().contains("serverFail"))
            }
        } finally {
            server.shutdown()
        }
    }

    private fun metadata(server: MockWebServer): OAuthServerMetadata {
        return OAuthServerMetadata(
            issuer = "test",
            deviceAuthorizationEndpoint = server.url("/device").toString(),
            tokenEndpoint = server.url("/token").toString(),
            authorizationEndpoint = null,
            registrationEndpoint = null,
            introspectionEndpoint = null,
            grantTypesSupported = emptyList(),
            responseTypesSupported = null,
            scopesSupported = null
        )
    }

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
}

private class FakeTokenStoreAccess(initialToken: StoredToken?) : TokenStoreAccess {
    var currentToken: StoredToken? = initialToken
    val savedTokens = mutableListOf<TokenResponse>()

    override fun getTokens(server: String, email: String): StoredToken? = currentToken

    override fun saveTokens(server: String, email: String, tokenResponse: TokenResponse) {
        savedTokens += tokenResponse
        currentToken = StoredToken(
            accessToken = tokenResponse.accessToken,
            tokenType = tokenResponse.tokenType,
            expiresAt = tokenResponse.expiresIn?.let { System.currentTimeMillis() + it * 1000L },
            refreshToken = tokenResponse.refreshToken
        )
    }

    override fun clearTokens(server: String, email: String) {
        currentToken = null
    }
}
