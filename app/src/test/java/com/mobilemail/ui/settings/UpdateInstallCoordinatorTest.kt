@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mobilemail.ui.settings

import com.mobilemail.domain.model.UpdateInstallState
import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.InstallCallback
import com.mobilemail.domain.port.InstallOutcome
import com.mobilemail.domain.port.InstallStartResult
import com.mobilemail.domain.port.PendingInstall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APK_PATH = "/data/updates/app.apk"
private const val ATTEMPT_ID = 42L

private fun manifest(versionCode: Int = 10600) = UpdateReleaseManifest(
    versionName = "1.6.0",
    versionCode = versionCode,
    applicationId = "app.turtleold.mobilemail",
    minSdk = 31,
    apkDownloadUrl = "https://example.com/app.apk",
    apkSizeBytes = 12_345_678L,
    apkSha256 = "a".repeat(64)
)

/**
 * Проверяет [UpdateInstallCoordinator] через его публичную границу: команды
 * [UpdateInstallCoordinator.onDownloadCompleted], [UpdateInstallCoordinator.install],
 * [UpdateInstallCoordinator.onAppForegrounded] и наблюдаемое
 * [UpdateInstallCoordinator.state]. Системный установщик подменяется
 * [FakeUpdateInstallPort], сохранённая попытка — [FakeUpdateInstallPersistencePort].
 */
class UpdateInstallCoordinatorTest {

    private fun CoroutineScope.coordinator(
        port: FakeUpdateInstallPort = FakeUpdateInstallPort(),
        store: FakeUpdateInstallPersistencePort = FakeUpdateInstallPersistencePort(),
        installedVersionCode: Int = 10500,
        apkExists: (String) -> Boolean = { true }
    ) = UpdateInstallCoordinator.create(
        scope = this,
        installPort = port,
        store = store,
        installedVersionCode = { installedVersionCode },
        apkExists = apkExists,
        nextAttemptId = { ATTEMPT_ID }
    )

    @Test
    fun `install with permission starts a session and waits for confirmation`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        assertTrue(coordinator.state.value is UpdateInstallState.Ready)

        coordinator.install(backgroundScope)
        runCurrent()

        assertEquals(listOf(APK_PATH to ATTEMPT_ID), port.startedInstalls)
        assertTrue(coordinator.state.value is UpdateInstallState.AwaitingConfirmation)
    }

    @Test
    fun `missing apk is not handed to the installer`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port, apkExists = { false })

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        assertTrue(port.startedInstalls.isEmpty())
        assertEquals(0, port.settingsOpenedCount)
        assertTrue(coordinator.state.value is UpdateInstallState.Failed)
    }

    @Test
    fun `without permission the coordinator opens settings and waits`() = runTest {
        val port = FakeUpdateInstallPort(permissionGranted = false)
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        assertEquals(1, port.settingsOpenedCount)
        assertTrue(port.startedInstalls.isEmpty())
        assertTrue(coordinator.state.value is UpdateInstallState.AwaitingPermission)
    }

    @Test
    fun `granting permission after settings continues the same attempt`() = runTest {
        val port = FakeUpdateInstallPort(permissionGranted = false)
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        port.permissionGranted = true
        coordinator.onAppForegrounded(backgroundScope, unlocked = true)
        runCurrent()

        assertEquals(listOf(APK_PATH to ATTEMPT_ID), port.startedInstalls)
        assertTrue(coordinator.state.value is UpdateInstallState.AwaitingConfirmation)
    }

    @Test
    fun `denying permission after settings returns to install or later`() = runTest {
        val port = FakeUpdateInstallPort(permissionGranted = false)
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        coordinator.onAppForegrounded(backgroundScope, unlocked = true)
        runCurrent()

        assertTrue(port.startedInstalls.isEmpty())
        assertTrue(coordinator.state.value is UpdateInstallState.Ready)
    }

    @Test
    fun `pin lock defers permission resume until unlock`() = runTest {
        val port = FakeUpdateInstallPort(permissionGranted = false)
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()
        port.permissionGranted = true

        coordinator.onAppForegrounded(backgroundScope, unlocked = false)
        runCurrent()
        assertTrue(port.startedInstalls.isEmpty())
        assertTrue(coordinator.state.value is UpdateInstallState.AwaitingPermission)

        coordinator.onAppForegrounded(backgroundScope, unlocked = true)
        runCurrent()
        assertEquals(listOf(APK_PATH to ATTEMPT_ID), port.startedInstalls)
    }

    @Test
    fun `auto continue after a foreground download starts exactly one install`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = true)
        runCurrent()
        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = true)
        runCurrent()

        assertEquals(1, port.startedInstalls.size)
        assertTrue(coordinator.state.value is UpdateInstallState.AwaitingConfirmation)
    }

    @Test
    fun `successful install is reported as installed`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        port.emit(InstallCallback(ATTEMPT_ID, InstallOutcome.Success))
        runCurrent()

        assertTrue(coordinator.state.value is UpdateInstallState.Installed)
    }

    @Test
    fun `cancelled install keeps the apk and retries only by a new command`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        port.emit(InstallCallback(ATTEMPT_ID, InstallOutcome.Cancelled))
        runCurrent()

        val cancelled = coordinator.state.value
        assertTrue(cancelled is UpdateInstallState.Cancelled)
        assertEquals(APK_PATH, (cancelled as UpdateInstallState.Cancelled).apkFilePath)
        assertEquals(1, port.startedInstalls.size)

        coordinator.install(backgroundScope)
        runCurrent()

        assertEquals(2, port.startedInstalls.size)
        assertTrue(coordinator.state.value is UpdateInstallState.AwaitingConfirmation)
    }

    @Test
    fun `install failure is surfaced`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        port.emit(InstallCallback(ATTEMPT_ID, InstallOutcome.Failed("Недостаточно места для установки")))
        runCurrent()

        val failed = coordinator.state.value
        assertTrue(failed is UpdateInstallState.Failed)
        assertEquals(
            "Недостаточно места для установки",
            (failed as UpdateInstallState.Failed).error.getUserMessage()
        )
    }

    @Test
    fun `a duplicate callback does not start another install`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        port.emit(InstallCallback(ATTEMPT_ID, InstallOutcome.Success))
        runCurrent()
        port.emit(InstallCallback(ATTEMPT_ID, InstallOutcome.Success))
        runCurrent()

        assertEquals(1, port.startedInstalls.size)
        assertTrue(coordinator.state.value is UpdateInstallState.Installed)
    }

    @Test
    fun `a foreign callback does not change the active attempt`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        port.emit(InstallCallback(ATTEMPT_ID + 1, InstallOutcome.Success))
        runCurrent()

        assertTrue(coordinator.state.value is UpdateInstallState.AwaitingConfirmation)
    }

    @Test
    fun `pending user action is deferred in background and launched on return`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        port.emit(InstallCallback(ATTEMPT_ID, InstallOutcome.AwaitingUserAction))
        runCurrent()
        assertEquals(0, port.deferredUserActionLaunchCount)

        coordinator.onAppForegrounded(backgroundScope, unlocked = true)
        runCurrent()

        assertEquals(1, port.deferredUserActionLaunchCount)
        assertTrue(coordinator.state.value is UpdateInstallState.AwaitingConfirmation)
    }

    @Test
    fun `pending user action in foreground is launched immediately`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.onAppForegrounded(backgroundScope, unlocked = true)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        port.emit(InstallCallback(ATTEMPT_ID, InstallOutcome.AwaitingUserAction))
        runCurrent()

        assertEquals(1, port.deferredUserActionLaunchCount)
    }

    @Test
    fun `start failure is reported and does not leave an active attempt`() = runTest {
        val port = FakeUpdateInstallPort(startResult = InstallStartResult.Failed("Не удалось прочитать APK"))
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        val failed = coordinator.state.value
        assertTrue(failed is UpdateInstallState.Failed)
        assertEquals("Не удалось прочитать APK", (failed as UpdateInstallState.Failed).error.getUserMessage())

        port.emit(InstallCallback(ATTEMPT_ID, InstallOutcome.Success))
        runCurrent()
        assertTrue(coordinator.state.value is UpdateInstallState.Failed)
    }

    @Test
    fun `pending user action waits for unlock before launching`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        port.emit(InstallCallback(ATTEMPT_ID, InstallOutcome.AwaitingUserAction))
        runCurrent()
        assertEquals(0, port.deferredUserActionLaunchCount)

        coordinator.onAppForegrounded(backgroundScope, unlocked = false)
        runCurrent()
        assertEquals(0, port.deferredUserActionLaunchCount)

        coordinator.onAppForegrounded(backgroundScope, unlocked = true)
        runCurrent()
        assertEquals(1, port.deferredUserActionLaunchCount)
    }

    @Test
    fun `failure to open permission settings is surfaced`() = runTest {
        val port = FakeUpdateInstallPort(permissionGranted = false, settingsOpenSucceeds = false)
        val coordinator = backgroundScope.coordinator(port)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        assertTrue(port.startedInstalls.isEmpty())
        assertTrue(coordinator.state.value is UpdateInstallState.Failed)
    }

    @Test
    fun `starting an install persists the attempt`() = runTest {
        val port = FakeUpdateInstallPort()
        val store = FakeUpdateInstallPersistencePort()
        val coordinator = backgroundScope.coordinator(port, store)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = false)
        runCurrent()
        coordinator.install(backgroundScope)
        runCurrent()

        val pending = store.loadPendingInstall()
        assertEquals(APK_PATH, pending?.apkFilePath)
        assertEquals(10600, pending?.manifest?.versionCode)
    }

    @Test
    fun `success without callback is reconciled to installed on next launch`() = runTest {
        val port = FakeUpdateInstallPort()
        val store = FakeUpdateInstallPersistencePort(PendingInstall(manifest(), APK_PATH))
        val coordinator = backgroundScope.coordinator(port, store, installedVersionCode = 10600)

        runCurrent()

        assertTrue(coordinator.state.value is UpdateInstallState.Installed)
        assertTrue(port.startedInstalls.isEmpty())
        assertNull(store.loadPendingInstall())
    }

    @Test
    fun `unrecoverable attempt offers install without opening the installer`() = runTest {
        val port = FakeUpdateInstallPort()
        val store = FakeUpdateInstallPersistencePort(PendingInstall(manifest(), APK_PATH))
        val coordinator = backgroundScope.coordinator(port, store, installedVersionCode = 10500)

        runCurrent()

        assertTrue(coordinator.state.value is UpdateInstallState.Ready)
        assertTrue(port.startedInstalls.isEmpty())
        assertEquals(0, port.settingsOpenedCount)
    }

    @Test
    fun `unrecoverable attempt with a missing apk fails`() = runTest {
        val port = FakeUpdateInstallPort()
        val store = FakeUpdateInstallPersistencePort(PendingInstall(manifest(), APK_PATH))
        val coordinator = backgroundScope.coordinator(
            port,
            store,
            installedVersionCode = 10500,
            apkExists = { false }
        )

        runCurrent()

        assertTrue(coordinator.state.value is UpdateInstallState.Failed)
        assertEquals(0, port.settingsOpenedCount)
    }

    @Test
    fun `a download for an already installed version is reported installed`() = runTest {
        val port = FakeUpdateInstallPort()
        val coordinator = backgroundScope.coordinator(port, installedVersionCode = 10600)

        coordinator.onDownloadCompleted(backgroundScope, manifest(), APK_PATH, autoContinue = true)
        runCurrent()

        assertTrue(coordinator.state.value is UpdateInstallState.Installed)
        assertTrue(port.startedInstalls.isEmpty())
    }
}
