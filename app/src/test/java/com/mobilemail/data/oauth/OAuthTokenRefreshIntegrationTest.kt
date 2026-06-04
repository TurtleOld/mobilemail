package com.mobilemail.data.oauth

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OAuthTokenRefreshIntegrationTest {

    @Test
    fun `refreshToken parses successful response`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "access_token": "new_access",
                      "token_type": "Bearer",
                      "expires_in": 3600
                    }
                    """.trimIndent()
                )
        )
        server.start()
        try {
            val metadata = OAuthServerMetadata(
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

            val refresh = OAuthTokenRefresh(metadata, "mobilemail-test", OkHttpClient())
            val token = refresh.refreshToken("old_refresh")

            assertEquals("new_access", token.accessToken)
            assertEquals("Bearer", token.tokenType)
            assertEquals(3600, token.expiresIn)
            // Server did not return refresh_token, so old one must be reused.
            assertEquals("old_refresh", token.refreshToken)

            val request = server.takeRequest()
            assertEquals("/token", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("grant_type=refresh_token"))
            assertTrue(body.contains("client_id=mobilemail-test"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `refreshToken throws OAuthException on server error`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"invalid_grant","error_description":"refresh expired"}""")
        )
        server.start()
        try {
            val metadata = OAuthServerMetadata(
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

            val refresh = OAuthTokenRefresh(metadata, "mobilemail-test", OkHttpClient())
            try {
                refresh.refreshToken("expired")
                fail("Expected OAuthException")
            } catch (error: OAuthException) {
                assertEquals(400, error.statusCode)
                assertTrue(error.message.orEmpty().contains("refresh expired"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `refreshToken throws parse error when token payload is incomplete`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token_type":"Bearer"}""")
        )
        server.start()
        try {
            val metadata = OAuthServerMetadata(
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

            val refresh = OAuthTokenRefresh(metadata, "mobilemail-test", OkHttpClient())
            try {
                refresh.refreshToken("old_refresh")
                fail("Expected OAuthException for malformed token payload")
            } catch (error: OAuthException) {
                assertTrue(error.message.orEmpty().contains("Ошибка парсинга refresh token"))
            }
        } finally {
            server.shutdown()
        }
    }
}
