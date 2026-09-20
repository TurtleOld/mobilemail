package com.mobilemail.ui.settings

import android.os.Build
import com.mobilemail.BuildConfig
import com.mobilemail.data.oauth.OAuthHttpClientFactory
import com.mobilemail.data.update.GithubReleaseUpdateRepository

/**
 * Держит единственный [UpdateCheckCoordinator] на процесс приложения.
 *
 * Автопроверка при запуске и ручная проверка из настроек обращаются к одному
 * и тому же экземпляру, поэтому они разделяют состояние (включая «Позже») и
 * никогда не выполняются параллельно. Пересоздание Activity, повороты и
 * возврат из фона не создают новый экземпляр — только новый процесс.
 */
object UpdateCheckCoordinatorHolder {
    @Volatile
    private var instance: UpdateCheckCoordinator? = null

    fun get(): UpdateCheckCoordinator {
        return instance ?: synchronized(this) {
            instance ?: create().also { instance = it }
        }
    }

    private fun create(): UpdateCheckCoordinator {
        val repository = GithubReleaseUpdateRepository(
            httpClient = OAuthHttpClientFactory.forUpdateCheck(),
            repoOwnerAndName = BuildConfig.UPDATE_CHECK_REPO,
            expectedApplicationId = BuildConfig.APPLICATION_ID,
            deviceSdkInt = Build.VERSION.SDK_INT
        )
        return UpdateCheckCoordinator(repository)
    }
}
