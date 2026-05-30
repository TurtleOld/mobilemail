package com.mobilemail.data.oauth

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeviceFlowClientIntegrationTest {

    @Test
    fun `requestDeviceCode parses successful response`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "device_code": "dev-code",
                      "user_code": "ABCD-EFGH",
                      "verification_uri": "https://mail.example.com/verify",
                      "verification_uri_complete": "https://mail.example.com/verify?user_code=ABCD-EFGH",
                      "expires_in": 1800,
                      "interval": 3
                    }
                    """.trimIndent()
                )
        )
        try {
            val client = DeviceFlowClient(
                metadata = metadata(server),
                clientId = "mobilemail-test",
                client = OkHttpClient()
            )

            val response = client.requestDeviceCode(listOf("scope-a", "scope-b"))

            assertEquals("dev-code", response.deviceCode)
            assertEquals("ABCD-EFGH", response.userCode)
            assertEquals("https://mail.example.com/verify", response.verificationUri)
            assertEquals(
                "https://mail.example.com/verify?user_code=ABCD-EFGH",
                response.verificationUriComplete
            )
            assertEquals(1800, response.expiresIn)
            assertEquals(3, response.interval)

            val request = server.takeRequest()
            assertEquals("/device", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("client_id=mobilemail-test"))
            assertTrue(body.contains("scope=scope-a+scope-b") || body.contains("scope=scope-a%20scope-b"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `requestDeviceCode throws OAuthException on oauth error`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"access_denied","error_description":"user denied access"}""")
        )
        try {
            val client = DeviceFlowClient(
                metadata = metadata(server),
                clientId = "mobilemail-test",
                client = OkHttpClient()
            )

            try {
                client.requestDeviceCode()
                fail("Expected OAuthException")
            } catch (error: OAuthException) {
                assertEquals(400, error.statusCode)
                assertTrue(error.message.orEmpty().contains("Доступ запрещён"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `requestDeviceCode throws parse error on invalid json`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"device_code":"dev-only"}""")
        )
        try {
            val client = DeviceFlowClient(
                metadata = metadata(server),
                clientId = "mobilemail-test",
                client = OkHttpClient()
            )

            try {
                client.requestDeviceCode()
                fail("Expected OAuthException")
            } catch (error: OAuthException) {
                assertTrue(error.message.orEmpty().contains("Ошибка парсинга device code"))
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
}
