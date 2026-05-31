package com.mobilemail.data.oauth

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OAuthDiscoveryIntegrationTest {

    @Test
    fun `discover uses openid configuration when available`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "issuer": "https://mail.example.com",
                      "device_authorization_endpoint": "https://mail.example.com/oauth/device",
                      "token_endpoint": "https://mail.example.com/oauth/token",
                      "grant_types_supported": ["refresh_token"]
                    }
                    """.trimIndent()
                )
        )
        server.start()
        try {
            val discovery = OAuthDiscovery(OkHttpClient())
            val metadata = discovery.discover(server.url("/").toString())

            assertEquals("https://mail.example.com", metadata.issuer)
            assertEquals("https://mail.example.com/oauth/device", metadata.deviceAuthorizationEndpoint)
            assertEquals("https://mail.example.com/oauth/token", metadata.tokenEndpoint)
            assertEquals("/.well-known/openid-configuration", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `discover falls back to oauth authorization server endpoint`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "issuer": "https://mail.example.com",
                      "device_authorization_endpoint": "https://mail.example.com/oauth/device",
                      "token_endpoint": "https://mail.example.com/oauth/token",
                      "grant_types_supported": ["refresh_token"]
                    }
                    """.trimIndent()
                )
        )
        server.start()
        try {
            val discovery = OAuthDiscovery(OkHttpClient())
            val metadata = discovery.discover(server.url("/").toString())

            assertEquals("https://mail.example.com/oauth/token", metadata.tokenEndpoint)
            assertEquals("/.well-known/openid-configuration", server.takeRequest().path)
            assertEquals("/.well-known/openid-configuration", server.takeRequest().path)
            assertEquals("/.well-known/openid-configuration", server.takeRequest().path)
            assertEquals("/.well-known/oauth-authorization-server", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `discover throws when response misses required endpoints`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"issuer":"https://mail.example.com"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"issuer":"https://mail.example.com"}""")
        )
        server.start()
        try {
            val discovery = OAuthDiscovery(OkHttpClient())
            try {
                discovery.discover(server.url("/").toString())
                fail("Expected discovery to fail without required endpoints")
            } catch (error: Exception) {
                assertTrue(error.message.orEmpty().contains("Не удалось выполнить discovery запрос"))
            }
        } finally {
            server.shutdown()
        }
    }
}
