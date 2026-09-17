package com.mobilemail.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.BuildConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UpdateCheckViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = UpdateCheckCoordinatorHolder.get()

    val state: StateFlow<UpdateCheckUiState> = coordinator.state

    fun checkForUpdate() {
        viewModelScope.launch { coordinator.checkForUpdate(BuildConfig.VERSION_CODE) }
    }
}
