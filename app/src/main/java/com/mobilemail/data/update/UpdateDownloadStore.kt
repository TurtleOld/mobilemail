package com.mobilemail.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobilemail.domain.model.UpdateReleaseManifest
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
            prefs[DOWNLOAD_ID] = pending.downloadId
            prefs.remove(COMPLETED_AT_MILLIS)
        }
    }

    override suspend fun loadPendingDownload(): PendingDownload? {
        val prefs = context.updateDownloadDataStore.data.first()
        val manifestJson = prefs[MANIFEST_JSON]
        val apkFilePath = prefs[APK_FILE_PATH]
        val downloadId = prefs[DOWNLOAD_ID]
        if (manifestJson == null || apkFilePath == null || downloadId == null) return null

        val manifest = runCatching { UpdateReleaseManifest.fromJson(manifestJson) }.getOrNull() ?: return null
        return PendingDownload(manifest, apkFilePath, downloadId)
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

private fun UpdateReleaseManifest.toJson(): String {
    return org.json.JSONObject().apply {
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("applicationId", applicationId)
        put("minSdk", minSdk)
        put("apkDownloadUrl", apkDownloadUrl)
        put("apkSizeBytes", apkSizeBytes)
        put("apkSha256", apkSha256)
    }.toString()
}

private fun UpdateReleaseManifest.Companion.fromJson(json: String): UpdateReleaseManifest {
    val obj = org.json.JSONObject(json)
    return UpdateReleaseManifest(
        versionName = obj.getString("versionName"),
        versionCode = obj.getInt("versionCode"),
        applicationId = obj.getString("applicationId"),
        minSdk = obj.getInt("minSdk"),
        apkDownloadUrl = obj.getString("apkDownloadUrl"),
        apkSizeBytes = obj.getLong("apkSizeBytes"),
        apkSha256 = obj.getString("apkSha256")
    )
}
