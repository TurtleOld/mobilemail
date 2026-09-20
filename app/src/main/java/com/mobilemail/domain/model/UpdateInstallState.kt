package com.mobilemail.domain.model

import com.mobilemail.ui.common.AppError

/**
 * Состояние установки уже скачанного и проверенного обновления.
 *
 * [Ready] — предложение «Установить» / «Позже»; [AwaitingPermission] — ждём
 * выдачи разрешения на установку из этого источника; [AwaitingConfirmation] —
 * системный установщик уже получил APK и ждёт действия пользователя.
 */
sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState
    data class Ready(
        val manifest: UpdateReleaseManifest,
        val apkFilePath: String
    ) : UpdateInstallState

    data class AwaitingPermission(
        val manifest: UpdateReleaseManifest,
        val apkFilePath: String
    ) : UpdateInstallState

    data class AwaitingConfirmation(
        val manifest: UpdateReleaseManifest,
        val apkFilePath: String
    ) : UpdateInstallState

    data class Installed(val manifest: UpdateReleaseManifest) : UpdateInstallState

    data class Cancelled(
        val manifest: UpdateReleaseManifest,
        val apkFilePath: String
    ) : UpdateInstallState

    data class Failed(
        val error: AppError,
        val manifest: UpdateReleaseManifest,
        val apkFilePath: String
    ) : UpdateInstallState
}
