package com.mobilemail.data.repository

import android.util.Log
import com.mobilemail.data.model.Attachment
import org.json.JSONArray
import org.json.JSONObject

object AttachmentParser {
    fun parseAttachments(bodyStructure: Any?): List<Attachment> {
        if (bodyStructure == null) {
            return emptyList()
        }

        val attachments = mutableListOf<Attachment>()

        try {
            when (bodyStructure) {
                is JSONObject -> parseBodyPart(bodyStructure, attachments)
                is JSONArray -> {
                    for (i in 0 until bodyStructure.length()) {
                        parseBodyPart(bodyStructure.getJSONObject(i), attachments)
                    }
                }
                else -> Log.w("AttachmentParser", "bodyStructure неизвестного типа: ${bodyStructure.javaClass.name}")
            }
        } catch (e: Exception) {
            Log.e("AttachmentParser", "Ошибка парсинга вложений", e)
        }

        return attachments
    }

    private fun parseBodyPart(part: JSONObject, attachments: MutableList<Attachment>) {
        try {
            val (type, subtype) = resolveTypeAndSubtype(part)
            val dispositionObj = part.optJSONObject("disposition")
            val dispositionStr = part.optString("disposition", "")
            val dispositionType = dispositionObj?.optString("disposition", "") ?: dispositionStr

            val filename = resolveFilename(part, dispositionObj, dispositionStr)
            val hasFilename = !filename.isNullOrBlank() && filename != "null"
            val isAttachment = dispositionType.equals("attachment", ignoreCase = true)
            val isTextPart = type == "text" && (subtype == "plain" || subtype == "html")

            val looksLikeAttachment = if (isTextPart) isAttachment else isAttachment || hasFilename

            if (looksLikeAttachment) {
                collectAttachment(part, type, subtype, filename, isAttachment, attachments)
            }

            recurseSubParts(part, attachments)
        } catch (e: Exception) {
            Log.e("AttachmentParser", "Ошибка парсинга части bodyStructure", e)
        }
    }

    private fun resolveTypeAndSubtype(part: JSONObject): Pair<String, String> {
        var type = part.optString("type", "").lowercase()
        var subtype = part.optString("subtype", "").lowercase()
        if (type.contains("/") && subtype.isEmpty()) {
            val split = type.split("/", limit = 2)
            type = split.getOrNull(0) ?: type
            subtype = split.getOrNull(1) ?: subtype
        }
        return type to subtype
    }

    private fun resolveFilename(
        part: JSONObject,
        dispositionObj: JSONObject?,
        dispositionStr: String
    ): String? {
        val fromParams = resolveFilenameFromParams(dispositionObj)
        if (fromParams != null) return fromParams

        val dispStr = dispositionObj?.optString("disposition", "") ?: dispositionStr
        if (dispStr.isNotEmpty()) {
            val match = Regex("filename[*]?=['\"]?([^'\"\\r\\n]+)['\"]?", RegexOption.IGNORE_CASE).find(dispStr)
            match?.groupValues?.get(1)?.let { return it }
        }

        val nameValue = part.optString("name", "")
        return if (nameValue.isNotBlank() && nameValue != "null") nameValue else null
    }

    private fun resolveFilenameFromParams(dispositionObj: JSONObject?): String? {
        val params = dispositionObj?.optJSONObject("params") ?: return null
        val direct = params.optString("filename")
        if (direct.isNotBlank() && direct != "null") return direct
        params.keys().forEach { key ->
            if (key.startsWith("filename")) {
                val value = params.optString(key, "")
                if (value.isNotEmpty() && value != "null") return value
            }
        }
        return null
    }

    private fun collectAttachment(
        part: JSONObject,
        type: String,
        subtype: String,
        filename: String?,
        isAttachment: Boolean,
        attachments: MutableList<Attachment>
    ) {
        val blobId = part.optString("blobId", "")
        val partId = part.optString("partId", "")
        val size = part.optLong("size", 0)

        val attachmentId = when {
            blobId.isNotEmpty() && blobId != "null" -> blobId
            partId.isNotEmpty() && partId != "null" -> partId
            else -> ""
        }

        val finalFilename = resolveDefaultFilename(filename, isAttachment, type, subtype)

        if (attachmentId.isNotEmpty() && finalFilename != null) {
            attachments.add(
                Attachment(
                    id = attachmentId,
                    filename = finalFilename,
                    mime = if (type.isNotEmpty() && subtype.isNotEmpty()) "$type/$subtype" else "application/octet-stream",
                    size = size
                )
            )
        }
    }

    private fun resolveDefaultFilename(
        filename: String?,
        isAttachment: Boolean,
        type: String,
        subtype: String
    ): String? {
        if (!filename.isNullOrBlank() && filename != "null") return filename
        if (!isAttachment) return null
        val ext = when {
            subtype.isNotEmpty() -> subtype
            type == "image" -> "jpg"
            type == "application" -> "bin"
            else -> "dat"
        }
        return "attachment.$ext"
    }

    private fun recurseSubParts(part: JSONObject, attachments: MutableList<Attachment>) {
        val parts = part.optJSONArray("parts") ?: part.optJSONArray("subParts") ?: return
        for (i in 0 until parts.length()) {
            parseBodyPart(parts.getJSONObject(i), attachments)
        }
    }
}
