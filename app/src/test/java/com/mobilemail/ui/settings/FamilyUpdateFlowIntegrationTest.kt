@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mobilemail.ui.settings

import com.mobilemail.data.update.GithubReleaseUpdateRepository
import com.mobilemail.domain.model.UpdateCheckResult
import com.mobilemail.domain.model.UpdateDownloadState
import com.mobilemail.domain.model.UpdateInstallState
import com.mobilemail.domain.port.DownloadStatus
import com.mobilemail.domain.port.InstallCallback
import com.mobilemail.domain.port.InstallOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APPLICATION_ID = "app.turtleold.mobilemail"
private const val DEVICE_SDK = 34
private const val APK_PATH = "/data/updates/app-release.apk"
private const val INSTALLED_CODE = 10504
private const val NEXT_ATTEMPT_ID = 42L

class FamilyUpdateFlowIntegrationTest {

    private fun repository(server: MockWebServer) = GithubReleaseUpdateRepository(
        httpClient = OkHttpClient(),
        repoOwnerAndName = "turtleold/mobilemail",
        expectedApplicationId = APPLICATION_ID,
        deviceSdkInt = DEVICE_SDK,
        apiBaseUrl = server.url("/").toString().trimEnd('/')
    )

    private fun enqueueFamilyRelease(server: MockWebServer) {
        val releases = """
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
        val metadata = """
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
        server.enqueue(MockResponse().setResponseCode(200).setBody(releases))
        server.enqueue(MockResponse().setResponseCode(200).setBody(metadata))
    }

    private fun CoroutineScope.wire(
        download: UpdateDownloadCoordinator,
        install: UpdateInstallCoordinator,
        isUserPresent: () -> Boolean
    ) {
        launch {
            download.state.collect { state ->
                when (state) {
                    is UpdateDownloadState.Ready -> install.onDownloadCompleted(
                        scope = this@wire,
                        manifest = state.manifest,
                        apkFilePath = state.apkFilePath,
                        autoContinue = state.autoContinue && isUserPresent()
                    )
                    is UpdateDownloadState.Expired -> install.onApkExpired(this@wire, state.manifest)
                    else -> Unit
                }
            }
        }
    }

    private fun downloadCoordinator(downloadPort: FakeUpdateDownloadPort) = UpdateDownloadCoordinator(
        downloadPort = downloadPort,
        verifier = FakeApkVerifierPort(),
        store = FakeUpdateDownloadPersistencePort(),
        apkFilePathFor = { APK_PATH },
        cleaner = FakeUpdateApkCleanupPort(),
        cleanupScheduler = FakeUpdateCleanupScheduler()
    )

    private fun CoroutineScope.installCoordinator(installPort: FakeUpdateInstallPort) = UpdateInstallCoordinator.create(
        scope = this,
        installPort = installPort,
        store = FakeUpdateInstallPersistencePort(),
        cleaner = FakeUpdateApkCleanupPort(),
        installedVersionCode = { INSTALLED_CODE },
        apkExists = { true },
        nextAttemptId = { NEXT_ATTEMPT_ID }
    )

    @Test
    fun `a newer family release is discovered, downloaded and confirmed through the system installer`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            enqueueFamilyRelease(server)
            val downloadPort = FakeUpdateDownloadPort()
            val installPort = FakeUpdateInstallPort(permissionGranted = true)
            val download = downloadCoordinator(downloadPort)
            val install = backgroundScope.installCoordinator(installPort)
            backgroundScope.wire(download, install, isUserPresent = { true })

            val check = repository(server).checkForUpdate(INSTALLED_CODE)
            assertTrue(check is UpdateCheckResult.UpdateAvailable)
            val manifest = (check as UpdateCheckResult.UpdateAvailable).manifest
            assertEquals(APPLICATION_ID, manifest.applicationId)
            assertTrue(manifest.versionCode > INSTALLED_CODE)

            download.startDownload(backgroundScope, manifest)
            runCurrent()
            downloadPort.setStatus(1L, DownloadStatus.Successful(APK_PATH))
            advanceTimeBy(600)
            runCurrent()

            assertTrue(install.state.value is UpdateInstallState.AwaitingConfirmation)
            assertEquals(listOf(APK_PATH to NEXT_ATTEMPT_ID), installPort.startedInstalls)

            installPort.emit(InstallCallback(attemptId = NEXT_ATTEMPT_ID, outcome = InstallOutcome.Success))
            advanceTimeBy(600)
            runCurrent()

            assertTrue(install.state.value is UpdateInstallState.Installed)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a download that finishes in the background waits for an explicit install after return`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            enqueueFamilyRelease(server)
            val downloadPort = FakeUpdateDownloadPort()
            val installPort = FakeUpdateInstallPort(permissionGranted = true)
            val download = downloadCoordinator(downloadPort)
            val install = backgroundScope.installCoordinator(installPort)
            var userPresent = true
            backgroundScope.wire(download, install, isUserPresent = { userPresent })

            val manifest = (repository(server).checkForUpdate(INSTALLED_CODE) as UpdateCheckResult.UpdateAvailable).manifest

            download.startDownload(backgroundScope, manifest)
            runCurrent()
            userPresent = false
            downloadPort.setStatus(1L, DownloadStatus.Successful(APK_PATH))
            advanceTimeBy(600)
            runCurrent()

            assertTrue(install.state.value is UpdateInstallState.Ready)
            assertTrue(installPort.startedInstalls.isEmpty())

            install.install(backgroundScope)
            advanceTimeBy(600)
            runCurrent()

            assertTrue(install.state.value is UpdateInstallState.AwaitingConfirmation)
            assertEquals(listOf(APK_PATH to NEXT_ATTEMPT_ID), installPort.startedInstalls)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a denied permission keeps the download for a later explicit install`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            enqueueFamilyRelease(server)
            val downloadPort = FakeUpdateDownloadPort()
            val installPort = FakeUpdateInstallPort(permissionGranted = false)
            val download = downloadCoordinator(downloadPort)
            val install = backgroundScope.installCoordinator(installPort)
            backgroundScope.wire(download, install, isUserPresent = { true })

            val manifest = (repository(server).checkForUpdate(INSTALLED_CODE) as UpdateCheckResult.UpdateAvailable).manifest

            download.startDownload(backgroundScope, manifest)
            runCurrent()
            downloadPort.setStatus(1L, DownloadStatus.Successful(APK_PATH))
            advanceTimeBy(600)
            runCurrent()

            assertTrue(install.state.value is UpdateInstallState.AwaitingPermission)
            assertEquals(1, installPort.settingsOpenedCount)
            assertTrue(installPort.startedInstalls.isEmpty())

            installPort.permissionGranted = true
            install.onAppForegrounded(backgroundScope, unlocked = true)
            advanceTimeBy(600)
            runCurrent()

            assertTrue(install.state.value is UpdateInstallState.AwaitingConfirmation)
            assertEquals(listOf(APK_PATH to NEXT_ATTEMPT_ID), installPort.startedInstalls)
        } finally {
            server.shutdown()
        }
    }
}
