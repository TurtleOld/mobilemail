package com.mobilemail.data.update

import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.ApkVerificationResult
import java.io.File
import java.security.MessageDigest

/**
 * Данные об APK-файле, извлекаемые платформенно (PackageManager), но
 * сверяемые с манифестом чистой логикой ниже — без Android-зависимостей,
 * что делает контракт тестируемым в юните.
 */
data class ApkPackageFacts(
    val packageName: String,
    val versionCode: Int,
    val signatures: Set<String>?
)

/**
 * Сверяет скачанный APK с [UpdateReleaseManifest] и с уже установленным
 * приложением: размер, SHA-256, applicationId, versionCode, minSdk и
 * совместимость подписывающего сертификата. Не зависит от Android SDK,
 * поэтому проверяется напрямую в юнит-тестах.
 */
object ApkContract {

    fun verifySizeAndHash(file: File, manifest: UpdateReleaseManifest): ApkVerificationResult.Invalid? {
        if (file.length() != manifest.apkSizeBytes) {
            return ApkVerificationResult.Invalid("Размер скачанного файла не совпадает с ожидаемым")
        }
        val actualSha256 = sha256Of(file)
        if (!actualSha256.equals(manifest.apkSha256, ignoreCase = true)) {
            return ApkVerificationResult.Invalid("Контрольная сумма скачанного файла не совпадает")
        }
        return null
    }

    fun verifyPackageContract(
        facts: ApkPackageFacts,
        manifest: UpdateReleaseManifest,
        deviceSdkInt: Int
    ): ApkVerificationResult.Invalid? = when {
        facts.packageName != manifest.applicationId ->
            ApkVerificationResult.Invalid("Пакет APK не совпадает с MobileMail")
        facts.versionCode != manifest.versionCode ->
            ApkVerificationResult.Invalid("Версия APK не совпадает с ожидаемой")
        manifest.minSdk > deviceSdkInt ->
            ApkVerificationResult.Invalid("APK требует более новую версию Android")
        else -> null
    }

    /**
     * `installedSignatures == null` означает, что установленное приложение не
     * удалось прочитать (например, первая установка) — в этом случае сверка
     * подписи пропускается, а не проваливается.
     */
    fun verifySignatureCompatibility(
        installedSignatures: Set<String>?,
        apkSignatures: Set<String>?
    ): ApkVerificationResult.Invalid? {
        if (installedSignatures == null || installedSignatures.isEmpty()) return null
        if (apkSignatures == null) return ApkVerificationResult.Invalid("Не удалось прочитать подпись APK")
        return if (installedSignatures != apkSignatures) {
            ApkVerificationResult.Invalid("Подпись APK несовместима с установленным приложением")
        } else {
            null
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
