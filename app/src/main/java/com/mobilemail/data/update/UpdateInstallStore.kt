package com.mobilemail.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobilemail.domain.port.PendingInstall
import com.mobilemail.domain.port.UpdateInstallPersistencePort
import kotlinx.coroutines.flow.first

private val Context.updateInstallDataStore: DataStore<Preferences> by preferencesDataStore(name = "update_install")

class UpdateInstallStore(private val context: Context) : UpdateInstallPersistencePort {
    private companion object {
        val MANIFEST_JSON = stringPreferencesKey("manifest_json")
        val APK_FILE_PATH = stringPreferencesKey("apk_file_path")
    }

    override suspend fun savePendingInstall(pending: PendingInstall) {
        context.updateInstallDataStore.edit { prefs ->
            prefs[MANIFEST_JSON] = pending.manifest.toJson()
            prefs[APK_FILE_PATH] = pending.apkFilePath
        }
    }

    override suspend fun loadPendingInstall(): PendingInstall? {
        val prefs = context.updateInstallDataStore.data.first()
        val manifestJson = prefs[MANIFEST_JSON]
        val apkFilePath = prefs[APK_FILE_PATH]
        if (manifestJson == null || apkFilePath == null) return null

        val manifest = updateManifestFromJson(manifestJson) ?: return null
        return PendingInstall(manifest, apkFilePath)
    }

    override suspend fun clear() {
        context.updateInstallDataStore.edit { prefs -> prefs.clear() }
    }
}
