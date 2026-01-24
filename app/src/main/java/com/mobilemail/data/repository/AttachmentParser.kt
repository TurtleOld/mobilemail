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
                is JSONObject -> {
                    parseBodyPart(bodyStructure, attachments)
                }
                is JSONArray -> {
                    for (i in 0 until bodyStructure.length()) {
                        val part = bodyStructure.getJSONObject(i)
                        parseBodyPart(part, attachments)
                    }
                }
                else -> {
                    Log.w("AttachmentParser", "bodyStructure неизвестного типа: ${bodyStructure.javaClass.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("AttachmentParser", "Ошибка парсинга вложений", e)
        }
        
        return attachments
    }
    
    private fun parseBodyPart(part: JSONObject, attachments: MutableList<Attachment>) {
        try {
            val disposition = part.optJSONObject("disposition")
            val dispositionStr = part.optString("disposition", "")
            val dispositionType = disposition?.optString("disposition", "") ?: dispositionStr
            
            var type = part.optString("type", "").lowercase()
            var subtype = part.optString("subtype", "").lowercase()
            if (type.contains("/") && subtype.isEmpty()) {
                val split = type.split("/", limit = 2)
                type = split.getOrNull(0) ?: type
                subtype = split.getOrNull(1) ?: subtype
            }
            
            val isTextPart = type == "text" && (subtype == "plain" || subtype == "html")
            
            var filename: String? = null
            disposition?.optJSONObject("params")?.let { params ->
                val directFilename = params.optString("filename")
                if (directFilename.isNotBlank() && directFilename != "null") {
                    filename = directFilename
                }
            }
            if (filename.isNullOrBlank() || filename == "null") {
                disposition?.optJSONObject("params")?.let { params ->
                    params.keys().forEach { key ->
                        if (key.startsWith("filename")) {
                            val value = params.optString(key, "")
                            if (value.isNotEmpty() && value != "null") {
                                filename = value
                            }
                        }
                    }
                }
            }
            if (filename.isNullOrBlank() || filename == "null") {
                val dispStr = disposition?.optString("disposition", "") ?: dispositionStr
                if (dispStr.isNotEmpty()) {
                    val filenameMatch = Regex("filename[*]?=['\"]?([^'\"\\r\\n]+)['\"]?", RegexOption.IGNORE_CASE).find(dispStr)
                    filename = filenameMatch?.groupValues?.get(1)
                }
            }
            if (filename.isNullOrBlank() || filename == "null") {
                val nameValue = part.optString("name", "")
                if (nameValue.isNotBlank() && nameValue != "null") {
                    filename = nameValue
                }
            }
            
            val hasFilename = !filename.isNullOrBlank() && filename != "null"
            val isAttachment = dispositionType.equals("attachment", ignoreCase = true)
            
            // Вложение, если:
            // 1. Явно указано disposition="attachment" (даже без имени файла)
            // 2. ИЛИ есть имя файла И это не текст/HTML/multipart
            // 3. НО: text/html или text/plain БЕЗ disposition="attachment" - это НЕ вложение
            val looksLikeAttachment = if (isTextPart) {
                // Текстовые части - только если явно указано attachment
                isAttachment
            } else {
                // Для остальных - если есть attachment или есть имя файла
                isAttachment || hasFilename
            }
            
            if (looksLikeAttachment) {
                // Для загрузки вложения нужен blobId, а не partId
                val blobId = part.optString("blobId", "")
                val partId = part.optString("partId", "")
                val size = part.optLong("size", 0)
                
                // Если blobId нет, используем partId (для некоторых серверов)
                val attachmentId = if (blobId.isNotEmpty() && blobId != "null") {
                    blobId
                } else if (partId.isNotEmpty() && partId != "null") {
                    partId
                } else {
                    ""
                }
                
                // Если имени файла нет, но есть явный attachment, используем имя по умолчанию на основе типа
                val finalFilename = if (filename.isNullOrBlank() || filename == "null") {
                    if (isAttachment) {
                        // Если это явное вложение без имени, создаем имя на основе типа
                        val ext = when {
                            subtype.isNotEmpty() -> subtype
                            type == "image" -> "jpg"
                            type == "application" -> "bin"
                            else -> "dat"
                        }
                        "attachment.$ext"
                    } else {
                        null
                    }
                } else {
                    filename
                }
                
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
            
            // Рекурсивно обрабатываем вложенные части (для multipart)
            // Проверяем и parts, и subParts (разные серверы используют разные поля)
            val parts = part.optJSONArray("parts") ?: part.optJSONArray("subParts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val subPart = parts.getJSONObject(i)
                    parseBodyPart(subPart, attachments)
                }
            }
        } catch (e: Exception) {
            Log.e("AttachmentParser", "Ошибка парсинга части bodyStructure", e)
        }
    }
}
