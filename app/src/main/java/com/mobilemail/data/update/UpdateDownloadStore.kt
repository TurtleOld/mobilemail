package com.mobilemail.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobilemail.domain.port.PendingDownload
import com.mobilemail.domain.port.UpdateDownloadPersistencePort
import kotlinx.coroutines.flow.first

private val Context.updateDownloadDataStore: DataStore<Preferences> by preferencesDataStore(name = "update_download")

class UpdateDownloadStore(private val context: Context) : UpdateDownloadPersistencePort {
    private companion object {
        val MANIFEST_JSON = stringPreferencesKey("manifest_json")
        val APK_FILE_PATH = stringPreferencesKey("apk_file_path")
        val DOWNLOAD_ID = longPreferencesKey("download_id")
        val COMPLETED_AT_MILLIS = longPreferencesKey("completed_at_millis")
    }

    override suspend fun savePendingDownload(pending: PendingDownload) {
        context.updateDownloadDataStore.edit { prefs ->
            prefs[MANIFEST_JSON] = pending.manifest.toJson()
            prefs[APK_FILE_PATH] = pending.apkFilePath
            if (pending.downloadId != null) {
                prefs[DOWNLOAD_ID] = pending.downloadId
            } else {
                prefs.remove(DOWNLOAD_ID)
            }
            prefs.remove(COMPLETED_AT_MILLIS)
        }
    }

    override suspend fun loadPendingDownload(): PendingDownload? {
        val prefs = context.updateDownloadDataStore.data.first()
        val manifestJson = prefs[MANIFEST_JSON]
        val apkFilePath = prefs[APK_FILE_PATH]
        if (manifestJson == null || apkFilePath == null) return null

        val manifest = updateManifestFromJson(manifestJson) ?: return null
        return PendingDownload(manifest, apkFilePath, prefs[DOWNLOAD_ID])
    }

    override suspend fun saveDownloadId(downloadId: Long) {
        context.updateDownloadDataStore.edit { prefs ->
            if (prefs.contains(MANIFEST_JSON) && prefs.contains(APK_FILE_PATH)) {
                prefs[DOWNLOAD_ID] = downloadId
            }
        }
    }

    override suspend fun markCompletedNow(completedAtMillis: Long) {
        context.updateDownloadDataStore.edit { prefs ->
            if (!prefs.contains(COMPLETED_AT_MILLIS)) {
                prefs[COMPLETED_AT_MILLIS] = completedAtMillis
            }
        }
    }

    override suspend fun getCompletedAtMillis(): Long? {
        return context.updateDownloadDataStore.data.first()[COMPLETED_AT_MILLIS]
    }

    override suspend fun clear() {
        context.updateDownloadDataStore.edit { prefs -> prefs.clear() }
    }
}
