package com.mobilemail.data.update

import com.mobilemail.domain.model.UpdateReleaseManifest
import org.json.JSONObject

internal fun UpdateReleaseManifest.toJson(): String {
    return JSONObject().apply {
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("applicationId", applicationId)
        put("minSdk", minSdk)
        put("apkDownloadUrl", apkDownloadUrl)
        put("apkSizeBytes", apkSizeBytes)
        put("apkSha256", apkSha256)
    }.toString()
}

internal fun updateManifestFromJson(json: String): UpdateReleaseManifest? {
    return runCatching {
        val obj = JSONObject(json)
        UpdateReleaseManifest(
            versionName = obj.getString("versionName"),
            versionCode = obj.getInt("versionCode"),
            applicationId = obj.getString("applicationId"),
            minSdk = obj.getInt("minSdk"),
            apkDownloadUrl = obj.getString("apkDownloadUrl"),
            apkSizeBytes = obj.getLong("apkSizeBytes"),
            apkSha256 = obj.getString("apkSha256")
        )
    }.getOrNull()
}
