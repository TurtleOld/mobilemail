package com.mobilemail.data.sync

import com.mobilemail.data.jmap.JmapOAuthClient
import com.mobilemail.data.jmap.TokenStoreAccess
import com.mobilemail.data.local.dao.PendingOperationDao
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.data.oauth.OAuthServerMetadata
import com.mobilemail.data.oauth.StoredToken
import com.mobilemail.data.oauth.TokenResponse
import com.mobilemail.data.repository.ComposeRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Сквозные сценарии отправки через офлайн-очередь: JMAP-клиент, репозиторий и переходы
 * статусов задания проверяются вместе, как в рабочем приложении.
 */
class OfflineQueueUnknownOutcomeIntegrationTest {

    @Test
    fun `lost submission response leaves the send unconfirmed and is not resent`() = runTest {
        val server = MockWebServer()
        server.start()
        enqueueSendSequence(server, MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try {
            val repository = ComposeRepository(client(server))
            val dao = FakePendingOperationDao(listOf(sendOperation()))
            val attempts = mutableListOf<Long>()

            val summary = processQueue(dao, repository, attempts)
            val summaryAfterRestart = processQueue(dao, repository, attempts)

            assertEquals(listOf(1L), attempts)
            assertEquals(OfflineQueueManager.STATUS_UNKNOWN_OUTCOME, dao.operation(1)?.status)
            assertTrue(dao.statusHistory.contains(1L to OfflineQueueManager.STATUS_SUBMITTING))
            assertNotNull(dao.operation(1)?.payload)
            assertEquals(1, summary.unknownOutcomeCount)
            assertEquals(0, summary.failedCount)
            assertEquals(1, summaryAfterRestart.unknownOutcomeCount)
            assertEquals(1, submissionRequestCount(server))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `send interrupted after the submission request is reported as unknown on restart`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val repository = ComposeRepository(client(server))
            val dao = FakePendingOperationDao(listOf(sendOperation(status = OfflineQueueManager.STATUS_SUBMITTING)))
            val attempts = mutableListOf<Long>()

            val summary = processQueue(dao, repository, attempts)

            assertEquals(emptyList<Long>(), attempts)
            assertEquals(OfflineQueueManager.STATUS_UNKNOWN_OUTCOME, dao.operation(1)?.status)
            assertEquals(1, summary.unknownOutcomeCount)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `failure before the submission request keeps the send in the offline queue`() = runTest {
        val server = MockWebServer()
        server.start()
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
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try {
            val repository = ComposeRepository(client(server))
            val dao = FakePendingOperationDao(listOf(sendOperation()))

            val summary = processQueue(dao, repository, mutableListOf())

            assertEquals(OfflineQueueManager.STATUS_FAILED, dao.operation(1)?.status)
            assertEquals(1, summary.failedCount)
            assertEquals(0, summary.unknownOutcomeCount)
            assertEquals(0, submissionRequestCount(server))
        } finally {
            server.shutdown()
        }
    }

    private suspend fun processQueue(
        dao: PendingOperationDao,
        repository: ComposeRepository,
        attempts: MutableList<Long>
    ): OfflineQueueSummary = OfflineQueueManager.processOperations(dao, limit = 25) { operation, onBeforeSubmission ->
        attempts += operation.id
        OfflineQueueSendExecutor.execute(repository, operation.payload, EMAIL, onBeforeSubmission)
    }

    private fun client(server: MockWebServer): JmapOAuthClient {
        val client = JmapOAuthClient(
            baseUrl = server.url("/").toString(),
            email = EMAIL,
            accountId = "acc1",
            tokenStoreAccess = FakeTokenStoreAccess(
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
        client.withReadTimeout(200)
        return client
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
                    ["Identity/get", {"list":[{"id":"identity1","email":"$EMAIL"}]}, "i1"]
                  ]
                }
                """.trimIndent()
            )
        )
        server.enqueue(submission)
    }

    private fun submissionRequestCount(server: MockWebServer): Int {
        val requests = (0 until server.requestCount).map { server.takeRequest() }
        return requests.count { it.body.readUtf8().contains("EmailSubmission/set") }
    }

    private fun sendOperation(status: String = OfflineQueueManager.STATUS_PENDING) = PendingOperationEntity(
        id = 1,
        type = OfflineQueueManager.TYPE_SEND,
        server = "unused",
        email = EMAIL,
        accountId = "acc1",
        payload = JSONObject().apply {
            put("to", JSONArray().put("rcpt@example.com"))
            put("subject", "Subject")
            put("body", "Body")
            put("draftId", JSONObject.NULL)
            put("attachments", JSONArray())
        }.toString(),
        status = status,
        attemptCount = 0,
        lastError = null,
        createdAt = 1,
        updatedAt = 1
    )

    private fun JmapOAuthClient.withReadTimeout(millis: Long) {
        val field = JmapOAuthClient::class.java.getDeclaredField("client")
        field.isAccessible = true
        val current = field.get(this) as OkHttpClient
        field.set(this, current.newBuilder().readTimeout(millis, TimeUnit.MILLISECONDS).build())
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

    private class FakePendingOperationDao(initial: List<PendingOperationEntity>) : PendingOperationDao {
        private val operations = initial.toMutableList()
        val statusHistory = mutableListOf<Pair<Long, String>>()

        fun operation(id: Long): PendingOperationEntity? = operations.firstOrNull { it.id == id }

        override suspend fun resetRunning(updatedAt: Long) {
            operations.indices.forEach { index ->
                if (operations[index].status == OfflineQueueManager.STATUS_RUNNING) {
                    operations[index] = operations[index].copy(
                        status = OfflineQueueManager.STATUS_PENDING,
                        updatedAt = updatedAt
                    )
                }
            }
        }

        override suspend fun resetInterruptedSubmissions(updatedAt: Long) {
            operations.indices.forEach { index ->
                if (operations[index].status == OfflineQueueManager.STATUS_SUBMITTING) {
                    operations[index] = operations[index].copy(
                        status = OfflineQueueManager.STATUS_UNKNOWN_OUTCOME,
                        updatedAt = updatedAt
                    )
                }
            }
        }

        override suspend fun getProcessable(limit: Int): List<PendingOperationEntity> = operations
            .filter { it.status == OfflineQueueManager.STATUS_PENDING || it.status == OfflineQueueManager.STATUS_FAILED }
            .sortedBy { it.createdAt }
            .take(limit)

        override suspend fun updateStatus(
            id: Long,
            status: String,
            attemptCount: Int,
            lastError: String?,
            updatedAt: Long
        ) {
            statusHistory += id to status
            val index = operations.indexOfFirst { it.id == id }
            if (index >= 0) {
                operations[index] = operations[index].copy(
                    status = status,
                    attemptCount = attemptCount,
                    lastError = lastError,
                    updatedAt = updatedAt
                )
            }
        }

        override suspend fun deleteById(id: Long) {
            operations.removeAll { it.id == id }
        }

        override suspend fun getPendingCount(): Int = operations.count {
            it.status == OfflineQueueManager.STATUS_PENDING || it.status == OfflineQueueManager.STATUS_FAILED
        }

        override suspend fun getFailedCount(): Int = operations.count {
            it.status == OfflineQueueManager.STATUS_FAILED
        }

        override suspend fun getPermanentFailedCount(): Int = operations.count {
            it.status == OfflineQueueManager.STATUS_PERMANENT_FAILED
        }

        override suspend fun getUnknownOutcomeCount(): Int = operations.count {
            it.status == OfflineQueueManager.STATUS_UNKNOWN_OUTCOME
        }

        override suspend fun insert(operation: PendingOperationEntity): Long = error("not used")

        override suspend fun deleteByAccount(accountId: String) = error("not used")

        override fun observeAll(): Flow<List<PendingOperationEntity>> = emptyFlow()

        override suspend fun getById(id: Long): PendingOperationEntity? = error("not used")

        override suspend fun getAll(): List<PendingOperationEntity> = operations.toList()

        override suspend fun getActiveReadStatusOperations(): List<PendingOperationEntity> = error("not used")

        override suspend fun getFailedOperations(): List<PendingOperationEntity> = error("not used")

        override suspend fun delete(id: Long) = error("not used")

        override suspend fun retryById(id: Long, updatedAt: Long) = error("not used")

        override suspend fun retryAllFailed(updatedAt: Long) = error("not used")

        override suspend fun clearFailed() = error("not used")
    }

    private companion object {
        const val EMAIL = "user@example.com"
    }
}

private class FakeTokenStoreAccess(initialToken: StoredToken?) : TokenStoreAccess {
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
