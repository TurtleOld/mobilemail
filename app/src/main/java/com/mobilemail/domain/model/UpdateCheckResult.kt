package com.mobilemail.domain.model

import com.mobilemail.ui.common.AppError

sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val versionName: String,
        val apkSizeBytes: Long,
        val manifest: UpdateReleaseManifest
    ) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data object ReleaseNotReady : UpdateCheckResult()
    data class Failed(val error: AppError) : UpdateCheckResult()
}
