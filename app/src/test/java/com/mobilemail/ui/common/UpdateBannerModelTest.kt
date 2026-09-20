package com.mobilemail.ui.common

import com.mobilemail.domain.model.UpdateCheckResult
import com.mobilemail.domain.model.UpdateDownloadProgress
import com.mobilemail.domain.model.UpdateDownloadState
import com.mobilemail.domain.model.UpdateInstallState
import com.mobilemail.domain.model.UpdateReleaseManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun manifest(versionCode: Int = 10505) = UpdateReleaseManifest(
    versionName = "1.5.5",
    versionCode = versionCode,
    applicationId = "app.turtleold.mobilemail",
    minSdk = 31,
    apkDownloadUrl = "https://example.com/app.apk",
    apkSizeBytes = 12_345_678L,
    apkSha256 = "a".repeat(64)
)

private fun available() = UpdateCheckResult.UpdateAvailable(
    versionName = "1.5.5",
    apkSizeBytes = 12_345_678L,
    manifest = manifest()
)

class UpdateBannerModelTest {

    private fun resolve(
        check: UpdateCheckResult = UpdateCheckResult.Idle,
        download: UpdateDownloadState = UpdateDownloadState.Idle,
        install: UpdateInstallState = UpdateInstallState.Idle,
        isOfferDismissed: Boolean = false,
        isInstallOfferDismissed: Boolean = false
    ) = resolveUpdateBanner(check, download, install, isOfferDismissed, isInstallOfferDismissed)

    @Test
    fun `an available update is offered with later and update actions`() {
        val model = resolve(check = available())

        val offer = model as UpdateBannerModel.Offer
        assertEquals("1.5.5", offer.versionName)
        assertEquals(setOf(UpdateBannerAction.OFFER_LATER, UpdateBannerAction.UPDATE), offer.actions)
    }

    @Test
    fun `a dismissed offer stays hidden`() {
        assertNull(resolve(check = available(), isOfferDismissed = true))
    }

    @Test
    fun `an active download replaces the offer with progress and cancel`() {
        val model = resolve(
            check = available(),
            download = UpdateDownloadState.Downloading(UpdateDownloadProgress(500, 1000))
        )

        assertEquals(UpdateBannerModel.Downloading(UpdateDownloadProgress(500, 1000)), model)
        assertEquals(setOf(UpdateBannerAction.CANCEL), model!!.actions)
    }

    @Test
    fun `waiting for network is shown while the system download is paused`() {
        val model = resolve(check = available(), download = UpdateDownloadState.WaitingForNetwork)

        assertEquals(UpdateBannerModel.WaitingForNetwork, model)
    }

    @Test
    fun `a ready install offers install and later`() {
        val model = resolve(install = UpdateInstallState.Ready(manifest(), "/data/updates/app.apk"))

        val ready = model as UpdateBannerModel.ReadyToInstall
        assertEquals("1.5.5", ready.versionName)
        assertEquals(setOf(UpdateBannerAction.INSTALL_LATER, UpdateBannerAction.INSTALL), ready.actions)
    }

    @Test
    fun `a postponed install offer stays hidden`() {
        assertNull(
            resolve(
                install = UpdateInstallState.Ready(manifest(), "/data/updates/app.apk"),
                isInstallOfferDismissed = true
            )
        )
    }

    @Test
    fun `awaiting permission and awaiting confirmation are informational`() {
        val awaitingPermission = resolve(install = UpdateInstallState.AwaitingPermission(manifest(), "/a.apk"))
        val awaitingConfirmation = resolve(install = UpdateInstallState.AwaitingConfirmation(manifest(), "/a.apk"))

        assertEquals(UpdateBannerModel.AwaitingPermission, awaitingPermission)
        assertEquals(UpdateBannerModel.AwaitingConfirmation, awaitingConfirmation)
        assertTrue(awaitingPermission!!.actions.isEmpty())
        assertTrue(awaitingConfirmation!!.actions.isEmpty())
    }

    @Test
    fun `a cancelled install offers a retry`() {
        val model = resolve(install = UpdateInstallState.Cancelled(manifest(), "/a.apk"))

        val failed = model as UpdateBannerModel.InstallFailed
        assertEquals(setOf(UpdateBannerAction.RETRY_INSTALL), failed.actions)
    }

    @Test
    fun `a failed install surfaces the error with a retry`() {
        val model = resolve(
            install = UpdateInstallState.Failed(
                error = AppError.UnknownError("Не удалось запустить установку"),
                manifest = manifest(),
                apkFilePath = "/a.apk"
            )
        )

        val failed = model as UpdateBannerModel.InstallFailed
        assertEquals("Не удалось запустить установку", failed.message)
        assertEquals(setOf(UpdateBannerAction.RETRY_INSTALL), failed.actions)
    }

    @Test
    fun `a failed download surfaces the error with a retry`() {
        val model = resolve(
            check = available(),
            download = UpdateDownloadState.Failed(
                error = AppError.UnknownError("Недостаточно места на устройстве"),
                manifest = manifest()
            )
        )

        val failed = model as UpdateBannerModel.DownloadFailed
        assertEquals("Недостаточно места на устройстве", failed.message)
        assertEquals(setOf(UpdateBannerAction.RETRY_DOWNLOAD), failed.actions)
    }

    @Test
    fun `an expired download falls back to a fresh offer`() {
        val model = resolve(check = available(), download = UpdateDownloadState.Expired(manifest()))

        val offer = model as UpdateBannerModel.Offer
        assertEquals(setOf(UpdateBannerAction.OFFER_LATER, UpdateBannerAction.UPDATE), offer.actions)
    }

    @Test
    fun `an installed update is no longer offered`() {
        assertNull(resolve(install = UpdateInstallState.Installed(manifest())))
    }

    @Test
    fun `nothing is shown without an available update`() {
        assertNull(resolve(check = UpdateCheckResult.UpToDate))
    }
}
