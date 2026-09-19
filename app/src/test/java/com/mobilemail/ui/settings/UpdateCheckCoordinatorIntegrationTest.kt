package com.mobilemail.ui.settings

import app.cash.turbine.test
import com.mobilemail.data.update.GithubReleaseUpdateRepository
import com.mobilemail.domain.model.UpdateCheckResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APPLICATION_ID = "app.turtleold.mobilemail"
private const val DEVICE_SDK = 34

/**
 * Проверяет координатор ручной проверки обновлений через его публичную
 * границу: команду [UpdateCheckCoordinator.checkForUpdate] и наблюдаемое
 * [UpdateCheckCoordinator.state]. GitHub подменяется локальным MockWebServer.
 */
class UpdateCheckCoordinatorIntegrationTest {

    private fun repository(server: MockWebServer) = GithubReleaseUpdateRepository(
        httpClient = OkHttpClient(),
        repoOwnerAndName = "turtleold/mobilemail",
        expectedApplicationId = APPLICATION_ID,
        deviceSdkInt = DEVICE_SDK,
        apiBaseUrl = server.url("/").toString().trimEnd('/')
    )

    private fun releasesBody(server: MockWebServer): String = """
        [
          {
            "tag_name": "v1.5.5",
            "draft": false,
            "prerelease": false,
            "assets": [
              {"name": "update-metadata.json", "size": 512, "browser_download_url": "${server.url("/assets/update-metadata.json")}"},
              {"name": "app-release.apk", "size": 12345678, "browser_download_url": "${server.url("/assets/app-release.apk")}"}
            ]
          }
        ]
    """.trimIndent()

    private fun metadataBody(): String = """
        {
          "schemaVersion": 1,
          "applicationId": "$APPLICATION_ID",
          "versionCode": 10505,
          "versionName": "1.5.5",
          "minSdk": 31,
          "apkAssetName": "app-release.apk",
          "apkSizeBytes": 12345678,
          "apkSha256": "${"a".repeat(64)}"
        }
    """.trimIndent()

    @Test
    fun `checkForUpdate transitions from idle to checking to update available`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody()))

            val coordinator = UpdateCheckCoordinator(repository(server))

            coordinator.state.test {
                assertEquals(UpdateCheckResult.Idle, awaitItem())

                coordinator.checkForUpdate(currentVersionCode = 10504)

                assertEquals(UpdateCheckResult.Checking, awaitItem())
                val updateAvailable = awaitItem()
                assertTrue(updateAvailable is UpdateCheckResult.UpdateAvailable)
                assertEquals("1.5.5", (updateAvailable as UpdateCheckResult.UpdateAvailable).versionName)
                assertEquals(12345678L, updateAvailable.apkSizeBytes)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a check already running ignores a concurrent command`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody()))

            val coordinator = UpdateCheckCoordinator(repository(server))

            val job1 = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.checkForUpdate(currentVersionCode = 1)
            }
            val job2 = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.checkForUpdate(currentVersionCode = 1)
            }
            job1.join()
            job2.join()

            assertEquals(2, server.requestCount)
            assertTrue(coordinator.state.value is UpdateCheckResult.UpdateAvailable)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `repeating the command after completion runs a new check`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            repeat(2) {
                server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
                server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody()))
            }

            val coordinator = UpdateCheckCoordinator(repository(server))

            coordinator.checkForUpdate(currentVersionCode = 1)
            coordinator.checkForUpdate(currentVersionCode = 1)

            assertEquals(4, server.requestCount)
            assertTrue(coordinator.state.value is UpdateCheckResult.UpdateAvailable)
        } finally {
            server.shutdown()
        }
    }
}
