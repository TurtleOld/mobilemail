package com.mobilemail.ui.settings

import android.content.Context
import android.os.Environment
import com.mobilemail.data.update.AndroidUpdateDownloadPort
import com.mobilemail.data.update.ApkContractVerifier
import com.mobilemail.data.update.UpdateDownloadSignalBus
import com.mobilemail.data.update.UpdateDownloadStore
import com.mobilemail.data.update.updateApkFileName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Держит единственный [UpdateDownloadCoordinator] на процесс приложения,
 * как [UpdateCheckCoordinatorHolder] держит координатор проверки. Согласие,
 * прогресс и download ID переживают восстановление процесса через
 * [UpdateDownloadStore], а не через этот объект.
 */
object UpdateDownloadCoordinatorHolder {
    @Volatile
    private var instance: UpdateDownloadCoordinator? = null

    private val restoreScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun get(context: Context): UpdateDownloadCoordinator {
        val existing = instance
        if (existing != null) return existing

        synchronized(this) {
            val alreadyCreated = instance
            if (alreadyCreated != null) return alreadyCreated

            val applicationContext = context.applicationContext
            val coordinator = UpdateDownloadCoordinator.createAndRestore(
                scope = restoreScope,
                downloadPort = AndroidUpdateDownloadPort(applicationContext),
                verifier = ApkContractVerifier(applicationContext),
                store = UpdateDownloadStore(applicationContext),
                apkFilePathFor = { manifest ->
                    val dir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    File(dir, updateApkFileName(manifest)).absolutePath
                }
            )
            restoreScope.launch {
                UpdateDownloadSignalBus.signals.collect { downloadId ->
                    coordinator.reconcile(restoreScope, signaledDownloadId = downloadId)
                }
            }
            instance = coordinator
            return coordinator
        }
    }
}
