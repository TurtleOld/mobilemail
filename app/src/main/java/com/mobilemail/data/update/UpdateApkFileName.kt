package com.mobilemail.data.update

import com.mobilemail.domain.model.UpdateReleaseManifest

fun updateApkFileName(manifest: UpdateReleaseManifest): String =
    "mobilemail-update-${manifest.versionCode}.apk"
