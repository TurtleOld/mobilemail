package com.mobilemail.ui.settings

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.update.updateApkFileName
import com.mobilemail.domain.model.UpdateDownloadState
import com.mobilemail.domain.model.UpdateReleaseManifest
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Путь назначения APK в app-specific внешнем хранилище, общий для всех точек входа в скачивание. */
fun updateOfferApkFilePath(context: Context, manifest: UpdateReleaseManifest): String {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    return File(dir, updateApkFileName(manifest)).absolutePath
}

class UpdateDownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = UpdateDownloadCoordinatorHolder.get(application)

    val state: StateFlow<UpdateDownloadState> = coordinator.state

    /** Пользователь согласился скачать релиз, увидев версию и размер. */
    fun startDownload(manifest: UpdateReleaseManifest) {
        val apkFilePath = updateOfferApkFilePath(getApplication(), manifest)
        coordinator.startDownload(viewModelScope, manifest, apkFilePath)
    }

    fun cancelDownload() {
        coordinator.cancelDownload(viewModelScope)
    }

    fun retryDownload() {
        coordinator.retryDownload(viewModelScope)
    }
}
