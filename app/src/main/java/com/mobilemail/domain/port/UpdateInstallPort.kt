package com.mobilemail.domain.port

import kotlinx.coroutines.flow.Flow

/** Результат постановки APK в системный установщик. */
sealed interface InstallStartResult {
    data object Started : InstallStartResult
    data class Failed(val reason: String) : InstallStartResult
}

/** Итог одной попытки установки, как его сообщает системный установщик. */
sealed interface InstallOutcome {
    data object Success : InstallOutcome
    data object Cancelled : InstallOutcome

    /**
     * Системный установщик требует явного подтверждения пользователя. Само
     * подтверждение запускается отдельно, только из foreground — см.
     * [UpdateInstallPort.launchDeferredUserAction].
     */
    data object AwaitingUserAction : InstallOutcome

    data class Failed(val reason: String) : InstallOutcome
}

/**
 * Результат установки, привязанный к конкретной попытке: [attemptId] позволяет
 * координатору отличить свою активную попытку от повторного или постороннего
 * callback.
 */
data class InstallCallback(val attemptId: Long, val outcome: InstallOutcome)

/**
 * Граница к системному установщику (`PackageInstaller` в проде).
 *
 * Установка всегда требует действия пользователя: тихой установки здесь нет,
 * а разрешения Android не выдаются программно. Запуск системного подтверждения
 * ([launchDeferredUserAction]) допускается только из foreground.
 */
interface UpdateInstallPort {
    /** Разрешена ли установка из этого источника для собственного пакета. */
    fun isInstallPermissionGranted(): Boolean

    /** Открывает системные настройки разрешения установки только для этого приложения. */
    fun openInstallPermissionSettings(): Boolean

    /** Передаёт APK системному установщику; [attemptId] вернётся в callback. */
    suspend fun startInstall(apkFilePath: String, attemptId: Long): InstallStartResult

    /**
     * Запускает отложенное системное подтверждение, если установщик его запросил.
     * Возвращает `true`, если подтверждение действительно открыто.
     */
    fun launchDeferredUserAction(): Boolean

    val callbacks: Flow<InstallCallback>
}
