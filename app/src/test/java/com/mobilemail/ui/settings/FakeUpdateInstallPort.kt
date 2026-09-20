package com.mobilemail.ui.settings

import com.mobilemail.domain.port.InstallCallback
import com.mobilemail.domain.port.InstallStartResult
import com.mobilemail.domain.port.UpdateInstallPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Управляемая тестами реализация [UpdateInstallPort]: разрешение источника,
 * результат запуска сессии и отложенное подтверждение задаются вручную,
 * а callbacks подкладываются через [emit].
 */
class FakeUpdateInstallPort(
    var permissionGranted: Boolean = true,
    var startResult: InstallStartResult = InstallStartResult.Started,
    var deferredUserActionLaunches: Boolean = true,
    var settingsOpenSucceeds: Boolean = true
) : UpdateInstallPort {
    private val mutableCallbacks = MutableSharedFlow<InstallCallback>(extraBufferCapacity = 8)
    override val callbacks: Flow<InstallCallback> = mutableCallbacks.asSharedFlow()

    var settingsOpenedCount = 0
        private set
    val startedInstalls = mutableListOf<Pair<String, Long>>()
    var deferredUserActionLaunchCount = 0
        private set

    override fun isInstallPermissionGranted(): Boolean = permissionGranted

    override fun openInstallPermissionSettings(): Boolean {
        settingsOpenedCount++
        return settingsOpenSucceeds
    }

    override suspend fun startInstall(apkFilePath: String, attemptId: Long): InstallStartResult {
        startedInstalls.add(apkFilePath to attemptId)
        return startResult
    }

    override fun launchDeferredUserAction(): Boolean {
        deferredUserActionLaunchCount++
        return deferredUserActionLaunches
    }

    fun emit(callback: InstallCallback) {
        mutableCallbacks.tryEmit(callback)
    }
}
