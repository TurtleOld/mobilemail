@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mobilemail.ui.settings

import com.mobilemail.domain.model.UpdateDownloadState
import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.ApkVerificationResult
import com.mobilemail.domain.port.DownloadStatus
import com.mobilemail.domain.port.PendingDownload
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APK_PATH = "/data/updates/app.apk"

private fun manifest(versionCode: Int = 10505) = UpdateReleaseManifest(
    versionName = "1.5.5",
    versionCode = versionCode,
    applicationId = "app.turtleold.mobilemail",
    minSdk = 31,
    apkDownloadUrl = "https://example.com/app.apk",
    apkSizeBytes = 12_345_678L,
    apkSha256 = "a".repeat(64)
)

/**
 * Проверяет [UpdateDownloadCoordinator] через его публичную границу: команды
 * [UpdateDownloadCoordinator.startDownload], [UpdateDownloadCoordinator.cancelDownload],
 * [UpdateDownloadCoordinator.retryDownload], [UpdateDownloadCoordinator.restorePendingDownload]
 * и наблюдаемое [UpdateDownloadCoordinator.state]. Системный DownloadManager и проверка
 * APK подменяются fake-портами.
 */
class UpdateDownloadCoordinatorTest {

    private fun coordinator(
        downloadPort: FakeUpdateDownloadPort = FakeUpdateDownloadPort(),
        verifier: FakeApkVerifierPort = FakeApkVerifierPort(),
        store: FakeUpdateDownloadPersistencePort = FakeUpdateDownloadPersistencePort()
    ) = UpdateDownloadCoordinator(downloadPort, verifier, store)

    @Test
    fun `consent starts a request that transitions through downloading to ready`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val coordinator = coordinator(downloadPort = downloadPort)

        coordinator.startDownload(backgroundScope, manifest(), APK_PATH)
        runCurrent()

        assertEquals(1, downloadPort.enqueuedManifests.size)
        val downloadId = 1L
        assertEquals(UpdateDownloadState.Requesting, coordinator.state.value)

        downloadPort.setStatus(downloadId, DownloadStatus.Running(bytesDownloaded = 100, totalBytes = 1000))
        advanceTimeBy(600); runCurrent()
        val downloading = coordinator.state.value
        assertTrue(downloading is UpdateDownloadState.Downloading)
        assertEquals(100L, (downloading as UpdateDownloadState.Downloading).progress.bytesDownloaded)
        assertEquals(1000L, downloading.progress.totalBytes)

        downloadPort.setStatus(downloadId, DownloadStatus.Successful(APK_PATH))
        advanceTimeBy(600); runCurrent()

        val ready = coordinator.state.value
        assertTrue(ready is UpdateDownloadState.Ready)
        assertEquals(APK_PATH, (ready as UpdateDownloadState.Ready).apkFilePath)
    }

    @Test
    fun `unknown total size is reported as null, not a fabricated percentage`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val coordinator = coordinator(downloadPort = downloadPort)

        coordinator.startDownload(backgroundScope, manifest(), APK_PATH)
        runCurrent()
        downloadPort.setStatus(1L, DownloadStatus.Running(bytesDownloaded = 50, totalBytes = null))
        advanceTimeBy(600); runCurrent()

        val downloading = coordinator.state.value as UpdateDownloadState.Downloading
        assertNull(downloading.progress.totalBytes)
    }

    @Test
    fun `waiting for network is reported while the system download is paused`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val coordinator = coordinator(downloadPort = downloadPort)

        coordinator.startDownload(backgroundScope, manifest(), APK_PATH)
        runCurrent()
        downloadPort.setStatus(1L, DownloadStatus.Paused)
        advanceTimeBy(600); runCurrent()

        assertEquals(UpdateDownloadState.WaitingForNetwork, coordinator.state.value)
    }

    @Test
    fun `a second consent while a download is in progress does not start another download`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val coordinator = coordinator(downloadPort = downloadPort)
        val theManifest = manifest()

        coordinator.startDownload(backgroundScope, theManifest, APK_PATH)
        runCurrent()
        coordinator.startDownload(backgroundScope, theManifest, APK_PATH)
        runCurrent()

        assertEquals(1, downloadPort.enqueuedManifests.size)
    }

    @Test
    fun `consenting to a different release while one is downloading stops the old one first`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val coordinator = coordinator(downloadPort = downloadPort)

        coordinator.startDownload(backgroundScope, manifest(versionCode = 10505), APK_PATH)
        runCurrent()
        downloadPort.setStatus(1L, DownloadStatus.Running(500, 1000))
        advanceTimeBy(600); runCurrent()

        coordinator.startDownload(backgroundScope, manifest(versionCode = 10600), APK_PATH)
        runCurrent()

        assertEquals(2, downloadPort.enqueuedManifests.size)
        assertEquals(listOf(1L), downloadPort.cancelledIds)
        assertEquals(UpdateDownloadState.Requesting, coordinator.state.value)

        // Поздний сигнал по старой (отменённой) загрузке не воскрешает её.
        downloadPort.setStatus(1L, DownloadStatus.Successful(APK_PATH))
        advanceTimeBy(600); runCurrent()
        assertEquals(UpdateDownloadState.Requesting, coordinator.state.value)
    }

    @Test
    fun `a download error is surfaced with a retry available`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val coordinator = coordinator(downloadPort = downloadPort)

        coordinator.startDownload(backgroundScope, manifest(), APK_PATH)
        runCurrent()
        downloadPort.setStatus(1L, DownloadStatus.Failed("Недостаточно места на устройстве"))
        advanceTimeBy(600); runCurrent()

        val failed = coordinator.state.value
        assertTrue(failed is UpdateDownloadState.Failed)
        assertEquals("Недостаточно места на устройстве", (failed as UpdateDownloadState.Failed).error.getUserMessage())
    }

    @Test
    fun `retry after a failure starts a fresh download of the same release`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val coordinator = coordinator(downloadPort = downloadPort)

        coordinator.startDownload(backgroundScope, manifest(), APK_PATH)
        runCurrent()
        downloadPort.setStatus(1L, DownloadStatus.Failed("Ошибка сети при загрузке"))
        advanceTimeBy(600); runCurrent()

        coordinator.retryDownload(backgroundScope)
        runCurrent()

        assertEquals(2, downloadPort.enqueuedManifests.size)
        assertEquals(UpdateDownloadState.Requesting, coordinator.state.value)
    }

    @Test
    fun `cancel stops the system download and removes the partial file`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val store = FakeUpdateDownloadPersistencePort()
        val coordinator = coordinator(downloadPort = downloadPort, store = store)

        coordinator.startDownload(backgroundScope, manifest(), APK_PATH)
        runCurrent()
        downloadPort.setStatus(1L, DownloadStatus.Running(500, 1000))
        advanceTimeBy(600); runCurrent()

        coordinator.cancelDownload(backgroundScope)
        advanceTimeBy(600); runCurrent()

        assertEquals(listOf(1L), downloadPort.cancelledIds)
        assertEquals(UpdateDownloadState.Cancelled, coordinator.state.value)
        assertNull(store.loadPendingDownload())
    }

    @Test
    fun `a late completion signal after cancellation does not resurrect the download`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val coordinator = coordinator(downloadPort = downloadPort)

        coordinator.startDownload(backgroundScope, manifest(), APK_PATH)
        runCurrent()
        downloadPort.setStatus(1L, DownloadStatus.Running(500, 1000))
        advanceTimeBy(600); runCurrent()

        coordinator.cancelDownload(backgroundScope)
        advanceTimeBy(600); runCurrent()

        // Поздний сигнал completion об уже отменённой загрузке приходит из системы,
        // но координатор больше её не наблюдает (monitorJob отменён), поэтому
        // изменение статуса в fake-порте ни на что не влияет.
        downloadPort.setStatus(1L, DownloadStatus.Successful(APK_PATH))
        advanceTimeBy(600); runCurrent()

        assertEquals(UpdateDownloadState.Cancelled, coordinator.state.value)
    }

    @Test
    fun `re-consenting to a newer release after cancellation requires a fresh start`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val coordinator = coordinator(downloadPort = downloadPort)

        coordinator.startDownload(backgroundScope, manifest(versionCode = 10505), APK_PATH)
        runCurrent()
        coordinator.cancelDownload(backgroundScope)
        advanceTimeBy(600); runCurrent()

        assertEquals(UpdateDownloadState.Cancelled, coordinator.state.value)

        coordinator.startDownload(backgroundScope, manifest(versionCode = 10600), APK_PATH)
        runCurrent()

        assertEquals(2, downloadPort.enqueuedManifests.size)
        assertEquals(UpdateDownloadState.Requesting, coordinator.state.value)
    }

    @Test
    fun `an invalid apk contract fails verification and does not become Ready`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val verifier = FakeApkVerifierPort(ApkVerificationResult.Invalid("Контрольная сумма не совпадает"))
        val coordinator = coordinator(downloadPort = downloadPort, verifier = verifier)

        coordinator.startDownload(backgroundScope, manifest(), APK_PATH)
        runCurrent()
        downloadPort.setStatus(1L, DownloadStatus.Successful(APK_PATH))
        advanceTimeBy(600); runCurrent()

        val failed = coordinator.state.value
        assertTrue(failed is UpdateDownloadState.Failed)
        assertEquals("Контрольная сумма не совпадает", (failed as UpdateDownloadState.Failed).error.getUserMessage())
    }

    @Test
    fun `restoring a pending download after process restart resumes monitoring it`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        downloadPort.setStatus(1L, DownloadStatus.Running(300, 1000))
        val store = FakeUpdateDownloadPersistencePort(
            pending = PendingDownload(manifest(), APK_PATH, downloadId = 1L)
        )
        val coordinator = coordinator(downloadPort = downloadPort, store = store)

        coordinator.restorePendingDownload(backgroundScope)
        advanceTimeBy(600); runCurrent()

        val downloading = coordinator.state.value
        assertTrue(downloading is UpdateDownloadState.Downloading)
        assertEquals(300L, (downloading as UpdateDownloadState.Downloading).progress.bytesDownloaded)
    }

    @Test
    fun `completion time is recorded once and does not move on repeated observation`() = runTest {
        val downloadPort = FakeUpdateDownloadPort()
        val store = FakeUpdateDownloadPersistencePort()
        var clock = 1_000L
        val coordinator = UpdateDownloadCoordinator(downloadPort, FakeApkVerifierPort(), store) { clock }

        coordinator.startDownload(backgroundScope, manifest(), APK_PATH)
        runCurrent()
        downloadPort.setStatus(1L, DownloadStatus.Successful(APK_PATH))
        advanceTimeBy(600); runCurrent()

        assertEquals(1_000L, store.getCompletedAtMillis())

        clock = 2_000L
        store.markCompletedNow(clock)
        assertEquals(1_000L, store.getCompletedAtMillis())
    }
}
