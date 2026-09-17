package com.mobilemail.data.update

import com.mobilemail.domain.model.UpdateCheckResult
import com.mobilemail.ui.common.AppError
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APPLICATION_ID = "app.turtleold.mobilemail"
private const val DEVICE_SDK = 34

class GithubReleaseUpdateRepositoryIntegrationTest {

    private fun repository(server: MockWebServer, deviceSdkInt: Int = DEVICE_SDK) = GithubReleaseUpdateRepository(
        httpClient = OkHttpClient(),
        repoOwnerAndName = "turtleold/mobilemail",
        expectedApplicationId = APPLICATION_ID,
        deviceSdkInt = deviceSdkInt,
        apiBaseUrl = server.url("/").toString().trimEnd('/')
    )

    private fun releasesBody(
        server: MockWebServer,
        tag: String = "v1.5.5",
        draft: Boolean = false,
        prerelease: Boolean = false,
        includeMetadataAsset: Boolean = true,
        includeApkAsset: Boolean = true,
        apkSize: Long = 12_345_678L
    ): String {
        val assets = buildString {
            append("[")
            if (includeMetadataAsset) {
                append(asset("update-metadata.json", 512, server.url("/assets/update-metadata.json").toString()))
            }
            if (includeApkAsset) {
                if (includeMetadataAsset) append(",")
                append(asset("app-release.apk", apkSize, server.url("/assets/app-release.apk").toString()))
            }
            append("]")
        }
        return """
            [
              {
                "tag_name": "$tag",
                "draft": $draft,
                "prerelease": $prerelease,
                "assets": $assets
              }
            ]
        """.trimIndent()
    }

    private fun asset(name: String, size: Long, url: String): String =
        """{"name": "$name", "size": $size, "browser_download_url": "$url"}"""

    private fun metadataBody(
        versionCode: Int = 10505,
        versionName: String = "1.5.5",
        applicationId: String = APPLICATION_ID,
        minSdk: Int = 31,
        apkAssetName: String = "app-release.apk",
        apkSizeBytes: Long = 12_345_678L
    ): String = """
        {
          "schemaVersion": 1,
          "applicationId": "$applicationId",
          "versionCode": $versionCode,
          "versionName": "$versionName",
          "minSdk": $minSdk,
          "apkAssetName": "$apkAssetName",
          "apkSizeBytes": $apkSizeBytes,
          "apkSha256": "${"a".repeat(64)}"
        }
    """.trimIndent()

    @Test
    fun `reports update available when release versionCode is greater`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody(versionCode = 10505)))

            val result = repository(server).checkForUpdate(currentVersionCode = 10504)

            assertTrue(result is UpdateCheckResult.UpdateAvailable)
            val updateAvailable = result as UpdateCheckResult.UpdateAvailable
            assertEquals("1.5.5", updateAvailable.versionName)
            assertEquals(12_345_678L, updateAvailable.apkSizeBytes)
            assertEquals(10505, updateAvailable.manifest.versionCode)
            assertEquals(APPLICATION_ID, updateAvailable.manifest.applicationId)
            assertEquals("a".repeat(64), updateAvailable.manifest.apkSha256)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `reports up to date when versionCode is equal`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody(versionCode = 10505)))

            val result = repository(server).checkForUpdate(currentVersionCode = 10505)

            assertEquals(UpdateCheckResult.UpToDate, result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `reports up to date when versionCode is lesser`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody(versionCode = 10505)))

            val result = repository(server).checkForUpdate(currentVersionCode = 10600)

            assertEquals(UpdateCheckResult.UpToDate, result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `skips releases with the wrong applicationId`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody(applicationId = "com.other.app")))

            val result = repository(server).checkForUpdate(currentVersionCode = 1)

            assertEquals(UpdateCheckResult.ReleaseNotReady, result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `skips releases whose minSdk exceeds the device sdk`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody(minSdk = 35)))

            val result = repository(server, deviceSdkInt = 34).checkForUpdate(currentVersionCode = 1)

            assertEquals(UpdateCheckResult.ReleaseNotReady, result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `skips draft and prerelease releases`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val body = """
                [
                  {"tag_name": "v1.6.0", "draft": true, "prerelease": false, "assets": []},
                  {"tag_name": "v1.5.9", "draft": false, "prerelease": true, "assets": []}
                ]
            """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            val result = repository(server).checkForUpdate(currentVersionCode = 1)

            assertEquals(UpdateCheckResult.ReleaseNotReady, result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `treats a missing metadata asset as a release that is not ready`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody(releasesBody(server, includeMetadataAsset = false))
            )

            val result = repository(server).checkForUpdate(currentVersionCode = 1)

            assertEquals(UpdateCheckResult.ReleaseNotReady, result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `treats a missing apk asset as a release that is not ready`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody(releasesBody(server, includeApkAsset = false))
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody()))

            val result = repository(server).checkForUpdate(currentVersionCode = 1)

            assertEquals(UpdateCheckResult.ReleaseNotReady, result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `treats an apk asset size mismatch as a release that is not ready`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server, apkSize = 999L)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody(apkSizeBytes = 12_345_678L)))

            val result = repository(server).checkForUpdate(currentVersionCode = 1)

            assertEquals(UpdateCheckResult.ReleaseNotReady, result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `treats a version code that disagrees with the tag as a release that is not ready`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server, tag = "v1.5.5")))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody(versionCode = 99999)))

            val result = repository(server).checkForUpdate(currentVersionCode = 1)

            assertEquals(UpdateCheckResult.ReleaseNotReady, result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `maps corrupted metadata json to a parse error`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
            server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

            val result = repository(server).checkForUpdate(currentVersionCode = 1)

            assertTrue(result is UpdateCheckResult.Failed)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `maps a network failure to a failed result`() = runTest {
        val server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.shutdown()

        val repo = GithubReleaseUpdateRepository(
            httpClient = OkHttpClient(),
            repoOwnerAndName = "turtleold/mobilemail",
            expectedApplicationId = APPLICATION_ID,
            deviceSdkInt = DEVICE_SDK,
            apiBaseUrl = baseUrl
        )

        val result = repo.checkForUpdate(currentVersionCode = 1)

        assertTrue(result is UpdateCheckResult.Failed)
        assertTrue((result as UpdateCheckResult.Failed).error is AppError.NetworkError)
    }

    @Test
    fun `maps a rate limited response to a distinct failure`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(403)
                    .addHeader("X-RateLimit-Remaining", "0")
                    .setBody("""{"message": "rate limited"}""")
            )

            val result = repository(server).checkForUpdate(currentVersionCode = 1)

            assertTrue(result is UpdateCheckResult.Failed)
            val error = (result as UpdateCheckResult.Failed).error
            assertTrue(error is AppError.ServerError)
            assertEquals(429, (error as AppError.ServerError).statusCode)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `reuses the cached release list on a 304 response`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).addHeader("ETag", "\"v1\"").setBody(releasesBody(server)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody(versionCode = 10505)))
            server.enqueue(MockResponse().setResponseCode(304))
            server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody(versionCode = 10505)))

            val repo = repository(server)
            val first = repo.checkForUpdate(currentVersionCode = 1)
            val second = repo.checkForUpdate(currentVersionCode = 1)

            assertEquals(first, second)
            server.takeRequest() // initial releases request, no cached ETag yet
            server.takeRequest() // metadata request for the first check
            val secondReleasesRequest = server.takeRequest()
            assertEquals("\"v1\"", secondReleasesRequest.getHeader("If-None-Match"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `rejects a metadata response larger than the configured limit`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
            val oversized = metadataBody().let { it + " ".repeat(20_000) }
            server.enqueue(MockResponse().setResponseCode(200).setBody(oversized))

            val repo = GithubReleaseUpdateRepository(
                httpClient = OkHttpClient(),
                repoOwnerAndName = "turtleold/mobilemail",
                expectedApplicationId = APPLICATION_ID,
                deviceSdkInt = DEVICE_SDK,
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
                maxMetadataBytes = 1024
            )

            val result = repo.checkForUpdate(currentVersionCode = 1)

            assertTrue(result is UpdateCheckResult.Failed)
        } finally {
            server.shutdown()
        }
    }
}
