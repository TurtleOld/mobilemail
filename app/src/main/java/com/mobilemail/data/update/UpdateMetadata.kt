package com.mobilemail.data.update

import org.json.JSONException
import org.json.JSONObject

class UpdateMetadataException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Метаданные готового релиза, публикуемые рядом с подписанным APK.
 */
data class UpdateMetadata(
    val schemaVersion: Int,
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val apkAssetName: String,
    val apkSizeBytes: Long,
    val apkSha256: String
) {
    companion object {
        private const val SHA256_HEX_LENGTH = 64

        fun parse(json: String): UpdateMetadata {
            val obj = parseJson(json)
            return try {
                buildMetadata(obj)
            } catch (e: JSONException) {
                throw UpdateMetadataException("Метаданные обновления повреждены: отсутствует обязательное поле", e)
            } catch (e: IllegalArgumentException) {
                throw UpdateMetadataException("Метаданные обновления повреждены: ${e.message}", e)
            }
        }

        private fun parseJson(json: String): JSONObject = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw UpdateMetadataException("Метаданные обновления повреждены", e)
        }

        private fun buildMetadata(obj: JSONObject): UpdateMetadata {
            val sha = obj.getString("apkSha256").trim().lowercase()
            require(isValidSha256(sha)) { "некорректный apkSha256" }

            return UpdateMetadata(
                schemaVersion = obj.getInt("schemaVersion"),
                applicationId = obj.getString("applicationId"),
                versionCode = obj.getInt("versionCode"),
                versionName = obj.getString("versionName"),
                minSdk = obj.getInt("minSdk"),
                apkAssetName = obj.getString("apkAssetName"),
                apkSizeBytes = obj.getLong("apkSizeBytes"),
                apkSha256 = sha
            )
        }

        private fun isValidSha256(sha: String): Boolean =
            sha.length == SHA256_HEX_LENGTH && sha.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
