package com.mobilemail.ui.settings

import android.content.Context
import com.mobilemail.data.update.AndroidUpdateInstallPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Держит единственный [UpdateInstallCoordinator] на процесс приложения, как
 * [UpdateDownloadCoordinatorHolder] держит координатор загрузки. Результаты
 * установки приходят в координатор из receiver через процессный мост, поэтому
 * holder живёт независимо от Activity.
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

            val coordinator = UpdateInstallCoordinator.create(
                scope = callbackScope,
                installPort = AndroidUpdateInstallPort(context.applicationContext)
            )
            instance = coordinator
            return coordinator
        }
    }
}
