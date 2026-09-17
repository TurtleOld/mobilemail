package com.mobilemail.ui.settings

import com.mobilemail.domain.model.UpdateCheckResult
import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.ui.common.AppError

sealed class UpdateCheckUiState {
    data object Idle : UpdateCheckUiState()
    data object Checking : UpdateCheckUiState()
    data class UpdateAvailable(
        val versionName: String,
        val apkSizeBytes: Long,
        val manifest: UpdateReleaseManifest
    ) : UpdateCheckUiState()
    data object UpToDate : UpdateCheckUiState()
    data object ReleaseNotReady : UpdateCheckUiState()
    data class Failed(val error: AppError) : UpdateCheckUiState()
}

internal fun UpdateCheckResult.toUiState(): UpdateCheckUiState = when (this) {
    is UpdateCheckResult.UpdateAvailable -> UpdateCheckUiState.UpdateAvailable(versionName, apkSizeBytes, manifest)
    UpdateCheckResult.UpToDate -> UpdateCheckUiState.UpToDate
    UpdateCheckResult.ReleaseNotReady -> UpdateCheckUiState.ReleaseNotReady
    is UpdateCheckResult.Failed -> UpdateCheckUiState.Failed(error)
}
