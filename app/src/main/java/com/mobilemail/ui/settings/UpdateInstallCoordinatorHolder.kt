package com.mobilemail.ui.settings

import android.content.Context
import com.mobilemail.BuildConfig
import com.mobilemail.data.update.AndroidUpdateInstallPort
import com.mobilemail.data.update.UpdateApkCleanerHolder
import com.mobilemail.data.update.UpdateInstallStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Держит единственный [UpdateInstallCoordinator] на процесс приложения, как
 * [UpdateDownloadCoordinatorHolder] держит координатор загрузки. Результаты
 * установки приходят в координатор из receiver через процессный мост, поэтому
 * holder живёт независимо от Activity; сохранённая попытка согласуется с
 * установленной версией через [UpdateInstallStore].
 */
object UpdateInstallCoordinatorHolder {
    @Volatile
    private var instance: UpdateInstallCoordinator? = null

    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun get(context: Context): UpdateInstallCoordinator {
        val existing = instance
        if (existing != null) return existing

        synchronized(this) {
            val alreadyCreated = instance
            if (alreadyCreated != null) return alreadyCreated

            val applicationContext = context.applicationContext
            val coordinator = UpdateInstallCoordinator.create(
                scope = callbackScope,
                installPort = AndroidUpdateInstallPort(applicationContext),
                store = UpdateInstallStore(applicationContext),
                cleaner = UpdateApkCleanerHolder.get(applicationContext),
                installedVersionCode = { BuildConfig.VERSION_CODE }
            )
            instance = coordinator
            return coordinator
        }
    }
}
