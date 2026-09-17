package com.mobilemail.data.update

import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.ApkVerificationResult
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

private const val APPLICATION_ID = "app.turtleold.mobilemail"

class ApkContractTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun manifest(
        versionCode: Int = 10505,
        minSdk: Int = 31,
        apkSizeBytes: Long = 8L,
        apkSha256: String = sha256Of("test-bytes".toByteArray())
    ) = UpdateReleaseManifest(
        versionName = "1.5.5",
        versionCode = versionCode,
        applicationId = APPLICATION_ID,
        minSdk = minSdk,
        apkDownloadUrl = "https://example.com/app.apk",
        apkSizeBytes = apkSizeBytes,
        apkSha256 = apkSha256
    )

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `accepts a file whose size and hash match the manifest`() {
        val bytes = "12345678".toByteArray()
        val file = tempFolder.newFile("app.apk").apply { writeBytes(bytes) }
        val manifest = manifest(apkSizeBytes = bytes.size.toLong(), apkSha256 = sha256Of(bytes))

        assertNull(ApkContract.verifySizeAndHash(file, manifest))
    }

    @Test
    fun `rejects a file whose size does not match the manifest`() {
        val file = tempFolder.newFile("app.apk").apply { writeBytes("short".toByteArray()) }
        val manifest = manifest(apkSizeBytes = 999L)

        val result = ApkContract.verifySizeAndHash(file, manifest)
        assertTrue(result is ApkVerificationResult.Invalid)
    }

    @Test
    fun `rejects a file whose hash does not match the manifest`() {
        val bytes = "12345678".toByteArray()
        val file = tempFolder.newFile("app.apk").apply { writeBytes(bytes) }
        val manifest = manifest(apkSizeBytes = bytes.size.toLong(), apkSha256 = "f".repeat(64))

        val result = ApkContract.verifySizeAndHash(file, manifest)
        assertTrue(result is ApkVerificationResult.Invalid)
    }

    @Test
    fun `accepts package facts that match the manifest`() {
        val facts = ApkPackageFacts(packageName = APPLICATION_ID, versionCode = 10505, signatures = null)
        assertNull(ApkContract.verifyPackageContract(facts, manifest(), deviceSdkInt = 34))
    }

    @Test
    fun `rejects a package name that does not match the manifest`() {
        val facts = ApkPackageFacts(packageName = "com.other.app", versionCode = 10505, signatures = null)
        val result = ApkContract.verifyPackageContract(facts, manifest(), deviceSdkInt = 34)
        assertTrue(result is ApkVerificationResult.Invalid)
    }

    @Test
    fun `rejects a versionCode that does not match the manifest`() {
        val facts = ApkPackageFacts(packageName = APPLICATION_ID, versionCode = 1, signatures = null)
        val result = ApkContract.verifyPackageContract(facts, manifest(versionCode = 10505), deviceSdkInt = 34)
        assertTrue(result is ApkVerificationResult.Invalid)
    }

    @Test
    fun `rejects an apk whose minSdk exceeds the device sdk`() {
        val facts = ApkPackageFacts(packageName = APPLICATION_ID, versionCode = 10505, signatures = null)
        val result = ApkContract.verifyPackageContract(facts, manifest(minSdk = 35), deviceSdkInt = 34)
        assertTrue(result is ApkVerificationResult.Invalid)
    }

    @Test
    fun `accepts matching signatures`() {
        val signatures = setOf("aabbcc")
        assertNull(ApkContract.verifySignatureCompatibility(signatures, signatures))
    }

    @Test
    fun `rejects an apk signed with a different certificate than the installed app`() {
        val result = ApkContract.verifySignatureCompatibility(setOf("aabbcc"), setOf("ddeeff"))
        assertTrue(result is ApkVerificationResult.Invalid)
    }

    @Test
    fun `rejects an apk whose signature could not be read`() {
        val result = ApkContract.verifySignatureCompatibility(setOf("aabbcc"), null)
        assertTrue(result is ApkVerificationResult.Invalid)
    }

    @Test
    fun `skips signature comparison when the installed app signature is unknown`() {
        assertNull(ApkContract.verifySignatureCompatibility(null, setOf("aabbcc")))
        assertNull(ApkContract.verifySignatureCompatibility(emptySet(), setOf("aabbcc")))
    }
}
