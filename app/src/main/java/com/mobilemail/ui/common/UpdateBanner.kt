package com.mobilemail.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobilemail.domain.model.UpdateCheckResult
import com.mobilemail.domain.model.UpdateDownloadProgress
import com.mobilemail.domain.model.UpdateDownloadState
import com.mobilemail.domain.model.UpdateInstallState
import java.util.Locale

enum class UpdateBannerAction {
    UPDATE,
    OFFER_LATER,
    INSTALL_LATER,
    CANCEL,
    INSTALL,
    RETRY_INSTALL,
    RETRY_DOWNLOAD
}

sealed interface UpdateBannerModel {
    val actions: Set<UpdateBannerAction>

    data class Offer(val versionName: String, val apkSizeBytes: Long) : UpdateBannerModel {
        override val actions: Set<UpdateBannerAction> = setOf(UpdateBannerAction.OFFER_LATER, UpdateBannerAction.UPDATE)
    }

    data object Preparing : UpdateBannerModel {
        override val actions: Set<UpdateBannerAction> = setOf(UpdateBannerAction.CANCEL)
    }

    data class Downloading(val progress: UpdateDownloadProgress) : UpdateBannerModel {
        override val actions: Set<UpdateBannerAction> = setOf(UpdateBannerAction.CANCEL)
    }

    data object WaitingForNetwork : UpdateBannerModel {
        override val actions: Set<UpdateBannerAction> = setOf(UpdateBannerAction.CANCEL)
    }

    data class ReadyToInstall(val versionName: String, val canPostpone: Boolean) : UpdateBannerModel {
        override val actions: Set<UpdateBannerAction> =
            if (canPostpone) {
                setOf(UpdateBannerAction.INSTALL_LATER, UpdateBannerAction.INSTALL)
            } else {
                setOf(UpdateBannerAction.INSTALL)
            }
    }

    data object AwaitingPermission : UpdateBannerModel {
        override val actions: Set<UpdateBannerAction> = emptySet()
    }

    data object AwaitingConfirmation : UpdateBannerModel {
        override val actions: Set<UpdateBannerAction> = emptySet()
    }

    data class InstallFailed(val message: String) : UpdateBannerModel {
        override val actions: Set<UpdateBannerAction> = setOf(UpdateBannerAction.RETRY_INSTALL)
    }

    data class DownloadFailed(val message: String) : UpdateBannerModel {
        override val actions: Set<UpdateBannerAction> = setOf(UpdateBannerAction.RETRY_DOWNLOAD)
    }
}

fun resolveUpdateBanner(
    check: UpdateCheckResult,
    download: UpdateDownloadState,
    install: UpdateInstallState,
    isOfferDismissed: Boolean,
    isInstallOfferDismissed: Boolean
): UpdateBannerModel? = when (install) {
    is UpdateInstallState.Ready -> readyToInstallOrHidden(install.manifest.versionName, isInstallOfferDismissed)
    is UpdateInstallState.AwaitingPermission -> UpdateBannerModel.AwaitingPermission
    is UpdateInstallState.AwaitingConfirmation -> UpdateBannerModel.AwaitingConfirmation
    is UpdateInstallState.Cancelled -> UpdateBannerModel.InstallFailed("Установка отменена")
    is UpdateInstallState.Failed -> UpdateBannerModel.InstallFailed(install.error.getUserMessage())
    is UpdateInstallState.Installed,
    is UpdateInstallState.Expired,
    UpdateInstallState.Idle -> resolveDownloadOrOffer(check, download, isOfferDismissed, isInstallOfferDismissed)
}

private fun readyToInstallOrHidden(versionName: String, isInstallOfferDismissed: Boolean): UpdateBannerModel? =
    if (isInstallOfferDismissed) null else UpdateBannerModel.ReadyToInstall(versionName, canPostpone = true)

private fun resolveDownloadOrOffer(
    check: UpdateCheckResult,
    download: UpdateDownloadState,
    isOfferDismissed: Boolean,
    isInstallOfferDismissed: Boolean
): UpdateBannerModel? = when (download) {
    UpdateDownloadState.Requesting, UpdateDownloadState.Verifying -> UpdateBannerModel.Preparing
    is UpdateDownloadState.Downloading -> UpdateBannerModel.Downloading(download.progress)
    UpdateDownloadState.WaitingForNetwork -> UpdateBannerModel.WaitingForNetwork
    is UpdateDownloadState.Failed -> UpdateBannerModel.DownloadFailed(download.error.getUserMessage())
    is UpdateDownloadState.Ready ->
        if (isInstallOfferDismissed) null
        else UpdateBannerModel.ReadyToInstall(download.manifest.versionName, canPostpone = false)
    else -> offerOrHidden(check, isOfferDismissed)
}

private fun offerOrHidden(check: UpdateCheckResult, isOfferDismissed: Boolean): UpdateBannerModel? =
    if (check is UpdateCheckResult.UpdateAvailable && !isOfferDismissed) {
        UpdateBannerModel.Offer(check.versionName, check.apkSizeBytes)
    } else {
        null
    }

@Composable
fun UpdateBanner(
    model: UpdateBannerModel,
    onAction: (UpdateBannerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val description = model.description()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                model.secondaryText()?.let { secondary ->
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        if (model.showsProgress()) {
            val progress = (model as? UpdateBannerModel.Downloading)?.progress
            DownloadProgressIndicator(
                bytesDownloaded = progress?.bytesDownloaded,
                totalBytes = progress?.totalBytes
            )
        }
        if (model.actions.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                model.actions.forEach { action ->
                    TextButton(onClick = { onAction(action) }) {
                        Text(action.label())
                    }
                }
            }
        }
    }
}

private fun UpdateBannerModel.description(): String = when (this) {
    is UpdateBannerModel.Offer -> "Доступна версия $versionName"
    UpdateBannerModel.Preparing -> "Подготовка обновления…"
    is UpdateBannerModel.Downloading -> downloadProgressText(progress.bytesDownloaded, progress.totalBytes)
    UpdateBannerModel.WaitingForNetwork -> "Ожидание сети…"
    is UpdateBannerModel.ReadyToInstall -> "Обновление $versionName готово к установке"
    UpdateBannerModel.AwaitingPermission -> "Разрешите установку обновлений для MobileMail в настройках"
    UpdateBannerModel.AwaitingConfirmation -> "Ожидание подтверждения установки…"
    is UpdateBannerModel.InstallFailed -> message
    is UpdateBannerModel.DownloadFailed -> message
}

private fun UpdateBannerModel.secondaryText(): String? = when (this) {
    is UpdateBannerModel.Offer -> "%.1f МБ".format(Locale.US, apkSizeBytes / (1024.0 * 1024.0))
    else -> null
}

private fun UpdateBannerModel.showsProgress(): Boolean = when (this) {
    UpdateBannerModel.Preparing, UpdateBannerModel.WaitingForNetwork, is UpdateBannerModel.Downloading -> true
    else -> false
}

private fun UpdateBannerAction.label(): String = when (this) {
    UpdateBannerAction.UPDATE -> "Обновить"
    UpdateBannerAction.OFFER_LATER, UpdateBannerAction.INSTALL_LATER -> "Позже"
    UpdateBannerAction.CANCEL -> "Отменить"
    UpdateBannerAction.INSTALL -> "Установить"
    UpdateBannerAction.RETRY_INSTALL, UpdateBannerAction.RETRY_DOWNLOAD -> "Повторить"
}

@Composable
private fun DownloadProgressIndicator(bytesDownloaded: Long?, totalBytes: Long?) {
    if (bytesDownloaded != null && totalBytes != null) {
        val progress = (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

private fun downloadProgressText(bytesDownloaded: Long, totalBytes: Long?): String {
    if (totalBytes == null || totalBytes <= 0L) return "Загрузка обновления…"
    val percent = (bytesDownloaded * 100 / totalBytes).coerceIn(0L, 100L)
    return "Загрузка обновления… $percent%"
}
