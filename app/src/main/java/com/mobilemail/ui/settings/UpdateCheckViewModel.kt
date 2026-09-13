package com.mobilemail.ui.settings

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.BuildConfig
import com.mobilemail.data.oauth.OAuthHttpClientFactory
import com.mobilemail.data.update.GithubReleaseUpdateRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val UPDATE_CHECK_TIMEOUT_SECONDS = 15L

class UpdateCheckViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = UpdateCheckCoordinator(
        GithubReleaseUpdateRepository(
            httpClient = OAuthHttpClientFactory.sharedClient(
                connectTimeoutSeconds = UPDATE_CHECK_TIMEOUT_SECONDS,
                readTimeoutSeconds = UPDATE_CHECK_TIMEOUT_SECONDS,
                writeTimeoutSeconds = UPDATE_CHECK_TIMEOUT_SECONDS,
                retryOnConnectionFailure = true
            ),
            repoOwnerAndName = BuildConfig.UPDATE_CHECK_REPO,
            expectedApplicationId = BuildConfig.APPLICATION_ID,
            deviceSdkInt = Build.VERSION.SDK_INT
        )
    )

    val state: StateFlow<UpdateCheckUiState> = coordinator.state

    fun checkForUpdate() {
        viewModelScope.launch { coordinator.checkForUpdate(BuildConfig.VERSION_CODE) }
    }
}
