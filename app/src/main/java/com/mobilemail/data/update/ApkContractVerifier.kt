package com.mobilemail.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.ApkVerificationResult
import com.mobilemail.domain.port.ApkVerifierPort
import java.io.File

/**
 * Проверяет скачанный APK-файл против [UpdateReleaseManifest] и против уже
 * установленного приложения, прежде чем он станет Готовым обновлением.
 * Сама сверка — в [ApkContract], здесь только платформенное чтение через
 * PackageManager.
 */
class ApkContractVerifier(private val context: Context) : ApkVerifierPort {

    override fun verify(apkFilePath: String, manifest: UpdateReleaseManifest): ApkVerificationResult {
        val file = File(apkFilePath)
        if (!file.exists()) return ApkVerificationResult.Invalid("Скачанный файл не найден")

        val failure = ApkContract.verifySizeAndHash(file, manifest) ?: verifyPackageAndSignature(apkFilePath, manifest)
        return failure ?: ApkVerificationResult.Valid
    }

    private fun verifyPackageAndSignature(
        apkFilePath: String,
        manifest: UpdateReleaseManifest
    ): ApkVerificationResult.Invalid? {
        val packageInfo = readApkPackageInfo(apkFilePath)
            ?: return ApkVerificationResult.Invalid("Не удалось прочитать APK")

        val apkFacts = packageFactsOf(packageInfo)
        return ApkContract.verifyPackageContract(apkFacts, manifest, Build.VERSION.SDK_INT)
            ?: ApkContract.verifySignatureCompatibility(installedSignatureFingerprints(), apkFacts.signatures)
    }

    private fun installedSignatureFingerprints(): Set<String>? {
        val installedInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, signingFlags())
        }.getOrNull() ?: return null
        return signatureFingerprintsOf(installedInfo)
    }

    private fun readApkPackageInfo(apkFilePath: String): PackageInfo? = runCatching {
        context.packageManager.getPackageArchiveInfo(apkFilePath, signingFlags())
    }.getOrNull()

    private fun packageFactsOf(packageInfo: PackageInfo): ApkPackageFacts = ApkPackageFacts(
        packageName = packageInfo.packageName,
        versionCode = versionCodeOf(packageInfo),
        signatures = signatureFingerprintsOf(packageInfo)
    )

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun signatureFingerprintsOf(packageInfo: PackageInfo): Set<String>? {
        val signatures = rawSignaturesOf(packageInfo) ?: return null
        return signatures.map { it.toCharsString() }.toSet()
    }

    private fun rawSignaturesOf(packageInfo: PackageInfo): Array<Signature>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return null
            signingInfo.apkContentsSigners ?: signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
    }

    private fun versionCodeOf(packageInfo: PackageInfo): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
}
