package com.mobilemail.domain.model

/**
 * Всё, что нужно для скачивания и проверки APK найденного обновления.
 *
 * Заполняется из уже провалидированных [com.mobilemail.data.update.GithubRelease]
 * и [com.mobilemail.data.update.UpdateMetadata] в момент, когда релиз признан
 * доступным для установки, чтобы координатор загрузки не заново обращался
 * к GitHub API за деталями.
 */
data class UpdateReleaseManifest(
    val versionName: String,
    val versionCode: Int,
    val applicationId: String,
    val minSdk: Int,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val apkSha256: String
) {
    companion object
}
