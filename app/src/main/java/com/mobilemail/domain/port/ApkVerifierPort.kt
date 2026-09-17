package com.mobilemail.domain.port

import com.mobilemail.domain.model.UpdateReleaseManifest

sealed class ApkVerificationResult {
    data object Valid : ApkVerificationResult()
    data class Invalid(val reason: String) : ApkVerificationResult()
}

/**
 * Проверяет, что скачанный файл действительно тот APK, который обещали
 * метаданные релиза, и что его можно поставить поверх установленного
 * приложения: размер, SHA-256, applicationId, versionCode, minSdk и
 * совместимость подписи с уже установленным пакетом.
 */
interface ApkVerifierPort {
    fun verify(apkFilePath: String, manifest: UpdateReleaseManifest): ApkVerificationResult
}
