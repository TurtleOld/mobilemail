package com.mobilemail.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import com.mobilemail.domain.port.InstallCallback
import com.mobilemail.domain.port.InstallStartResult
import com.mobilemail.domain.port.UpdateInstallPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Отдаёт скачанный APK системному [PackageInstaller] как полноценную сессию
 * установки. Тихая установка не поддерживается: система сама показывает
 * подтверждение, а координатор получает результат сессии по её идентификатору.
 *
 * Файл передаётся только если он существует: непригодный или исчезнувший APK
 * до установщика не доходит.
 */
class AndroidUpdateInstallPort(private val context: Context) : UpdateInstallPort {

    override val callbacks: Flow<InstallCallback> = UpdateInstallResultBus.callbacks

    override fun isInstallPermissionGranted(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings(): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    override suspend fun startInstall(apkFilePath: String, attemptId: Long): InstallStartResult =
        withContext(Dispatchers.IO) {
            val apkFile = File(apkFilePath)
            if (!apkFile.exists()) {
                return@withContext InstallStartResult.Failed("Файл обновления не найден")
            }
            runCatching { commitSession(apkFile, attemptId) }
                .fold(
                    onSuccess = { InstallStartResult.Started },
                    onFailure = { InstallStartResult.Failed(it.message ?: "Не удалось запустить установку") }
                )
        }

    override fun launchDeferredUserAction(): Boolean {
        val intent = UpdateInstallUserActionHolder.take() ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
            .onFailure { UpdateInstallUserActionHolder.restore(intent) }
            .isSuccess
    }

    private fun commitSession(apkFile: File, attemptId: Long) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apkFile.inputStream().use { input ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            session.commit(installCallbackIntent(attemptId).intentSender)
        }
    }

    private fun installCallbackIntent(attemptId: Long): PendingIntent {
        val callbackIntent = Intent(context, UpdateInstallReceiver::class.java)
            .setAction(ACTION_INSTALL_RESULT)
            .putExtra(EXTRA_ATTEMPT_ID, attemptId)
        return PendingIntent.getBroadcast(
            context,
            attemptId.toInt(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.mobilemail.action.INSTALL_RESULT"
        const val EXTRA_ATTEMPT_ID = "com.mobilemail.extra.INSTALL_ATTEMPT_ID"
    }
}
