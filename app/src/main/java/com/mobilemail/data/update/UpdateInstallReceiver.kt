package com.mobilemail.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.mobilemail.domain.port.InstallCallback
import com.mobilemail.domain.port.InstallOutcome

/**
 * Принимает результат системной установки и передаёт его в
 * [UpdateInstallResultBus]. Ничего не запускает из receiver: системное
 * подтверждение откладывается до foreground, чтобы не открываться поверх
 * другого приложения.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidUpdateInstallPort.ACTION_INSTALL_RESULT) return
        val attemptId = intent.getLongExtra(AndroidUpdateInstallPort.EXTRA_ATTEMPT_ID, INVALID_ATTEMPT_ID)
        if (attemptId == INVALID_ATTEMPT_ID) return

        val outcome = intent.toInstallOutcome()
        if (outcome is InstallOutcome.AwaitingUserAction) {
            UpdateInstallUserActionHolder.store(userActionIntent(intent))
        }
        UpdateInstallResultBus.publish(InstallCallback(attemptId, outcome))
    }

    private fun Intent.toInstallOutcome(): InstallOutcome = when (
        getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    ) {
        PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Success
        PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallOutcome.AwaitingUserAction
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Cancelled
        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallOutcome.Failed("Недостаточно места для установки")
        PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallOutcome.Failed("Установка заблокирована системой")
        PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallOutcome.Failed("Конфликт с установленным приложением")
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallOutcome.Failed("Обновление несовместимо с установленным приложением")
        PackageInstaller.STATUS_FAILURE_INVALID -> InstallOutcome.Failed("Файл обновления повреждён")
        else -> InstallOutcome.Failed(
            getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Не удалось установить обновление"
        )
    }

    @Suppress("DEPRECATION")
    private fun userActionIntent(intent: Intent): Intent? = intent.getParcelableExtra(Intent.EXTRA_INTENT)

    private companion object {
        const val INVALID_ATTEMPT_ID = -1L
    }
}
