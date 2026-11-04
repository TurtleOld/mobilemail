package com.mobilemail.data.jmap

import com.mobilemail.data.oauth.OAuthServerMetadata
import com.mobilemail.data.oauth.StoredToken
import com.mobilemail.data.oauth.TokenResponse
import com.mobilemail.data.repository.MessageActionsRepository
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет реальный JSON Email/set: смена одного флага не должна заменять весь набор
 * keywords, а снятие флага должно удалять путь через null.
 */
class JmapKeywordUpdateIntegrationTest {

    @Test
    fun `markAsRead patches only the seen keyword`() = runTest {
        withKeywordServer(updateResponses = 1) { server, client ->
            assertTrue(MessageActionsRepository(client).markAsRead("email1", true).getOrThrow())

            server.takeRequest()
            val update = updateFor(server.takeRequest(), "email1")

            assertEquals(1, update.length())
            assertFalse(update.has("keywords"))
            assertTrue(update.getBoolean("keywords/\$seen"))
        }
    }

    @Test
    fun `toggleStarred sets and removes only the flagged keyword`() = runTest {
        withKeywordServer(updateResponses = 2) { server, client ->
            val actions = MessageActionsRepository(client)
            assertTrue(actions.toggleStarred("email1", true).getOrThrow())
            assertTrue(actions.toggleStarred("email1", false).getOrThrow())

            server.takeRequest()
            val setStar = updateFor(server.takeRequest(), "email1")
            assertEquals(1, setStar.length())
            assertFalse(setStar.has("keywords"))
            assertTrue(setStar.getBoolean("keywords/\$flagged"))

            val removeStar = updateFor(server.takeRequest(), "email1")
            assertEquals(1, removeStar.length())
            assertFalse(removeStar.has("keywords"))
            assertTrue(removeStar.isNull("keywords/\$flagged"))
        }
    }

    @Test
    fun `updateKeywords escapes patch paths`() = runTest {
        withKeywordServer(updateResponses = 1) { server, client ->
            assertTrue(
                MessageActionsRepository(client)
                    .updateKeywords("email1", mapOf("custom/tag~1" to true))
                    .getOrThrow()
            )

            server.takeRequest()
            val update = updateFor(server.takeRequest(), "email1")

            assertEquals(1, update.length())
            assertTrue(update.getBoolean("keywords/custom~1tag~01"))
        }
    }

    private suspend fun withKeywordServer(
        updateResponses: Int,
        block: suspend (MockWebServer, JmapOAuthClient) -> Unit
    ) {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse(server)))
        repeat(updateResponses) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"methodResponses":[["Email/set", {"updated":{"email1":null}}, "0"]]}"""
                )
            )
        }
        try {
            block(server, client(server))
        } finally {
            server.shutdown()
        }
    }

    private fun client(server: MockWebServer) = JmapOAuthClient(
        baseUrl = server.url("/").toString(),
        email = "user@example.com",
        accountId = "acc1",
        tokenStoreAccess = FakeKeywordTokenStoreAccess(
            StoredToken(
                accessToken = "old_access",
                tokenType = "Bearer",
                expiresAt = System.currentTimeMillis() + 60_000,
                refreshToken = "old_refresh"
            )
        ),
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

    private fun updateFor(request: RecordedRequest, emailId: String): JSONObject =
        JSONObject(request.body.readUtf8())
            .getJSONArray("methodCalls").getJSONArray(0)
            .getJSONObject(1).getJSONObject("update").getJSONObject(emailId)

    private fun sessionResponse(server: MockWebServer): String = """
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

private class FakeKeywordTokenStoreAccess(private val token: StoredToken) : TokenStoreAccess {
    override fun getTokens(server: String, email: String): StoredToken = token

    override fun saveTokens(server: String, email: String, tokenResponse: TokenResponse) = Unit

    override fun clearTokens(server: String, email: String) = Unit
}
