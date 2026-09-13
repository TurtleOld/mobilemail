package com.mobilemail.data.update

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object GithubReleaseParser {
    fun parseReleases(json: String): List<GithubRelease> {
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            throw UpdateMetadataException("Список релизов GitHub повреждён", e)
        }
        return (0 until array.length()).map { index -> parseRelease(array.getJSONObject(index)) }
    }

    private fun parseRelease(obj: JSONObject): GithubRelease {
        val assetsArray = obj.optJSONArray("assets") ?: JSONArray()
        val assets = (0 until assetsArray.length()).map { index -> parseAsset(assetsArray.getJSONObject(index)) }
        return GithubRelease(
            tagName = obj.optString("tag_name", ""),
            draft = obj.optBoolean("draft", false),
            prerelease = obj.optBoolean("prerelease", false),
            assets = assets
        )
    }

    private fun parseAsset(obj: JSONObject): GithubReleaseAsset = GithubReleaseAsset(
        name = obj.optString("name", ""),
        sizeBytes = obj.optLong("size", -1L),
        downloadUrl = obj.optString("browser_download_url", "")
    )
}
