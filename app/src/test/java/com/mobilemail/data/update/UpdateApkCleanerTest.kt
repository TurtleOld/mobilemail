@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mobilemail.data.update

import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.PendingDownload
import com.mobilemail.domain.port.PendingInstall
import com.mobilemail.ui.settings.FakeUpdateDownloadPersistencePort
import com.mobilemail.ui.settings.FakeUpdateInstallPersistencePort
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет [UpdateApkCleaner] через управляемое время и реальные файлы:
 * границу срока, очистку только собственного файла, повторную проверку после
 * перезапуска, уже установленную версию и сериализацию с передачей установщику.
 */
class UpdateApkCleanerTest {

    private val manifest = UpdateReleaseManifest(
        versionName = "1.6.0",
        versionCode = 10600,
        applicationId = "app.turtleold.mobilemail",
        minSdk = 31,
        apkDownloadUrl = "https://example.com/app.apk",
        apkSizeBytes = 12_345_678L,
        apkSha256 = "a".repeat(64)
    )

    private fun tempDir(): File = Files.createTempDirectory("update-cleaner").toFile()

    @Test
    fun `expiry follows the recorded completion time and does not move on recheck`() = runTest {
        var clock = 1_000L
        val downloadStore = FakeUpdateDownloadPersistencePort(completedAtMillis = 1_000L)
        val cleaner = UpdateApkCleaner(downloadStore, FakeUpdateInstallPersistencePort(), now = { clock })

        assertFalse(cleaner.isExpired())

        clock = 1_000L + APK_EXPIRY_MILLIS - 1
        assertFalse(cleaner.isExpired())

        clock = 1_000L + APK_EXPIRY_MILLIS
        assertTrue(cleaner.isExpired())

        clock = Long.MAX_VALUE
        assertTrue(cleaner.isExpired())
    }

    @Test
    fun `cleaning removes only the update file and its records`() = runTest {
        val dir = tempDir()
        val apk = File(dir, "mobilemail-update-10600.apk").apply { writeText("apk") }
        val unrelated = File(dir, "letter.eml").apply { writeText("mail") }
        val downloadStore = FakeUpdateDownloadPersistencePort(
            pending = PendingDownload(manifest, apk.absolutePath, downloadId = 1L),
            completedAtMillis = 1_000L
        )
        val installStore = FakeUpdateInstallPersistencePort(PendingInstall(manifest, apk.absolutePath))
        val cleaner = UpdateApkCleaner(downloadStore, installStore, now = { 2_000L })

        cleaner.clean(apk.absolutePath)

        assertFalse(apk.exists())
        assertTrue(unrelated.exists())
        assertNull(downloadStore.loadPendingDownload())
        assertNull(installStore.loadPendingInstall())
    }

    @Test
    fun `a file already removed by the system resets the records without failing`() = runTest {
        val missing = File(tempDir(), "gone.apk")
        val downloadStore = FakeUpdateDownloadPersistencePort(
            pending = PendingDownload(manifest, missing.absolutePath, downloadId = 1L),
            completedAtMillis = 1_000L
        )
        val installStore = FakeUpdateInstallPersistencePort(PendingInstall(manifest, missing.absolutePath))
        val cleaner = UpdateApkCleaner(downloadStore, installStore, now = { 2_000L })

        cleaner.clean(missing.absolutePath)

        assertFalse(missing.exists())
        assertNull(downloadStore.loadPendingDownload())
        assertNull(installStore.loadPendingInstall())
    }

    @Test
    fun `an expired apk is cleaned by the background opportunity`() = runTest {
        val dir = tempDir()
        val apk = File(dir, "update.apk").apply { writeText("apk") }
        val downloadStore = FakeUpdateDownloadPersistencePort(
            pending = PendingDownload(manifest, apk.absolutePath, downloadId = 1L),
            completedAtMillis = 1_000L
        )
        val cleaner = UpdateApkCleaner(
            downloadStore,
            FakeUpdateInstallPersistencePort(),
            now = { 1_000L + APK_EXPIRY_MILLIS }
        )

        assertTrue(cleaner.cleanUpIfNeeded(installedVersionCode = 10_000))
        assertFalse(apk.exists())
        assertNull(downloadStore.loadPendingDownload())
    }

    @Test
    fun `an already released version is not cleaned while still usable`() = runTest {
        val dir = tempDir()
        val apk = File(dir, "update.apk").apply { writeText("apk") }
        val downloadStore = FakeUpdateDownloadPersistencePort(
            pending = PendingDownload(manifest, apk.absolutePath, downloadId = 1L),
            completedAtMillis = 1_000L
        )
        val cleaner = UpdateApkCleaner(
            downloadStore,
            FakeUpdateInstallPersistencePort(),
            now = { 2_000L }
        )

        assertFalse(cleaner.cleanUpIfNeeded(installedVersionCode = 10_500))
        assertTrue(apk.exists())
        assertTrue(downloadStore.loadPendingDownload() != null)
    }

    @Test
    fun `an installed version cleans the leftover apk`() = runTest {
        val dir = tempDir()
        val apk = File(dir, "update.apk").apply { writeText("apk") }
        val downloadStore = FakeUpdateDownloadPersistencePort(
            pending = PendingDownload(manifest, apk.absolutePath, downloadId = 1L),
            completedAtMillis = 1_000L
        )
        val cleaner = UpdateApkCleaner(
            downloadStore,
            FakeUpdateInstallPersistencePort(),
            now = { 2_000L }
        )

        assertTrue(cleaner.cleanUpIfNeeded(installedVersionCode = 10_600))
        assertFalse(apk.exists())
        assertNull(downloadStore.loadPendingDownload())
    }

    @Test
    fun `cleanup waits until an in-progress install transfer is finished`() = runTest {
        val dir = tempDir()
        val apk = File(dir, "update.apk").apply { writeText("apk") }
        val downloadStore = FakeUpdateDownloadPersistencePort(
            pending = PendingDownload(manifest, apk.absolutePath, downloadId = 1L),
            completedAtMillis = 1_000L
        )
        val cleaner = UpdateApkCleaner(downloadStore, FakeUpdateInstallPersistencePort(), now = { 2_000L })

        val transferStarted = CompletableDeferred<Unit>()
        val releaseTransfer = CompletableDeferred<Unit>()
        val transfer = launch {
            cleaner.duringInstallTransfer {
                transferStarted.complete(Unit)
                releaseTransfer.await()
            }
        }
        transferStarted.await()

        val cleanup = launch { cleaner.clean(apk.absolutePath) }
        runCurrent()
        assertTrue(apk.exists())

        releaseTransfer.complete(Unit)
        transfer.join()
        cleanup.join()
        assertFalse(apk.exists())
    }
}
