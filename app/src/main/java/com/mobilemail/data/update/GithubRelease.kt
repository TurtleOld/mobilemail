package com.mobilemail.data.update

data class GithubReleaseAsset(
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String
)

data class GithubRelease(
    val tagName: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GithubReleaseAsset>
)
