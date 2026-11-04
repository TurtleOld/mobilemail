package com.mobilemail.data.jmap

import com.mobilemail.data.oauth.OAuthServerMetadata
import com.mobilemail.data.oauth.StoredToken
import com.mobilemail.data.oauth.TokenResponse
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет, что JMAP-клиент отличает подтверждённый отказ и ошибку до отправки
 * от неизвестного результата EmailSubmission/set.
 */
class JmapSendOutcomeIntegrationTest {

    @Test
    fun `sendEmail reports unknown outcome when the submission request fails on the server`() = runTest {
        val server = MockWebServer()
        server.start()
        enqueueSendSequence(server, MockResponse().setResponseCode(502).setBody("""{"error":"bad_gateway"}"""))
        try {
            val client = client(server)

            try {
                sendEmail(client)
            } catch (expected: SendOutcomeUnknownException) {
                return@runTest
            }
            fail("Expected unknown submission outcome")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `sendEmail keeps a confirmed rejection distinct from an unknown outcome`() = runTest {
        val server = MockWebServer()
        server.start()
        enqueueSendSequence(
            server,
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "methodResponses": [
                    ["EmailSubmission/set", {"notCreated":{"submit":{"type":"forbiddenFrom","description":"Not allowed"}}}, "c2"]
                  ]
                }
                """.trimIndent()
            )
        )
        try {
            val client = client(server)

            try {
                sendEmail(client)
                fail("Expected confirmed rejection")
            } catch (expected: Exception) {
                assertFalse(expected is SendOutcomeUnknownException)
                assertTrue(expected.message.orEmpty().contains("Not allowed"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `sendEmail reports unknown outcome when the response has no submission id`() = runTest {
        val server = MockWebServer()
        server.start()
        enqueueSendSequence(
            server,
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "methodResponses": [
                    ["EmailSubmission/set", {}, "c2"]
                  ]
                }
                """.trimIndent()
            )
        )
        try {
            val client = client(server)

            try {
                sendEmail(client)
                fail("Expected unknown submission outcome")
            } catch (expected: SendOutcomeUnknownException) {
                assertTrue(expected.message.orEmpty().contains("без id"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `sendEmail notifies the checkpoint right before the submission request`() = runTest {
        val server = MockWebServer()
        server.start()
        enqueueSendSequence(server, MockResponse().setResponseCode(502).setBody("""{"error":"bad_gateway"}"""))
        try {
            val client = client(server)
            var checkpointCalled = false

            try {
                sendEmail(client, onBeforeSubmission = { checkpointCalled = true })
            } catch (expected: SendOutcomeUnknownException) {
                assertTrue(checkpointCalled)
                return@runTest
            }
            fail("Expected unknown submission outcome")
        } finally {
            server.shutdown()
        }
    }

    private suspend fun sendEmail(client: JmapOAuthClient, onBeforeSubmission: (suspend () -> Unit)? = null) {
        client.sendEmail(
            from = "user@example.com",
            to = listOf("rcpt@example.com"),
            subject = "Subject",
            body = "Body",
            attachments = emptyList(),
            draftId = null,
            accountId = "acc1",
            onBeforeSubmission = onBeforeSubmission
        )
    }

    private fun client(server: MockWebServer): JmapOAuthClient {
        return JmapOAuthClient(
            baseUrl = server.url("/").toString(),
            email = "user@example.com",
            accountId = "acc1",
            tokenStoreAccess = FakeSendTokenStoreAccess(
                StoredToken(
                    accessToken = "access",
                    tokenType = "Bearer",
                    expiresAt = System.currentTimeMillis() + 60_000,
                    refreshToken = "refresh"
                )
            ),
            metadata = metadata(server),
            clientId = "mobilemail-test"
        )
    }

    private fun enqueueSendSequence(server: MockWebServer, submission: MockResponse) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse(server)))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "methodResponses": [
                    ["Mailbox/get", {"list":[{"id":"drafts","name":"Drafts","role":"drafts"}]}, "0"]
                  ]
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "methodResponses": [
                    ["Email/set", {"created":{"draft":{"id":"draft1"}}}, "d1"]
                  ]
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "methodResponses": [
                    ["Identity/get", {"list":[{"id":"identity1","email":"user@example.com"}]}, "i1"]
                  ]
                }
                """.trimIndent()
            )
        )
        server.enqueue(submission)
    }

    private fun metadata(server: MockWebServer): OAuthServerMetadata {
        return OAuthServerMetadata(
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

private class FakeSendTokenStoreAccess(initialToken: StoredToken?) : TokenStoreAccess {
    private var currentToken: StoredToken? = initialToken

    override fun getTokens(server: String, email: String): StoredToken? = currentToken

    override fun saveTokens(server: String, email: String, tokenResponse: TokenResponse) {
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
