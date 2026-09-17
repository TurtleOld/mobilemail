package com.mobilemail.ui.settings

import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.ApkVerificationResult
import com.mobilemail.domain.port.ApkVerifierPort

class FakeApkVerifierPort(
    private val result: ApkVerificationResult = ApkVerificationResult.Valid
) : ApkVerifierPort {
    val verifiedFilePaths = mutableListOf<String>()

    override fun verify(apkFilePath: String, manifest: UpdateReleaseManifest): ApkVerificationResult {
        verifiedFilePaths.add(apkFilePath)
        return result
    }
}
