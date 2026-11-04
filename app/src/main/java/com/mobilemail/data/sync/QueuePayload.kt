package com.mobilemail.data.sync

import com.mobilemail.data.model.Attachment
import org.json.JSONArray
import org.json.JSONObject

/**
 * Разбирает строковый список из полезной нагрузки операции очереди.
 *
 * @return элементы списка.
 */
internal fun JSONArray.toStringList(): List<String> {
    return buildList {
        for (index in 0 until length()) add(getString(index))
    }
}

/**
 * Разбирает вложения из полезной нагрузки операции очереди.
 *
 * @return вложения; отсутствующие необязательные поля получают значения по умолчанию.
 */
internal fun JSONArray.toAttachmentList(): List<Attachment> {
    return buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                Attachment(
                    id = item.getString("id"),
                    filename = item.getString("filename"),
                    mime = item.getString("mime"),
                    size = item.getLong("size"),
                    localFilePath = item.optString("localFilePath").takeIf { it.isNotBlank() && it != "null" },
                    isUploaded = item.optBoolean("isUploaded", true)
                )
            )
        }
    }
}

/**
 * Возвращает пути локальных копий вложений из полезной нагрузки отправки.
 *
 * @param payload JSON операции [OfflineQueueManager.TYPE_SEND].
 * @return пути копий; пустой список, если payload повреждён или копий нет.
 */
internal fun sendAttachmentPaths(payload: String): List<String> {
    val attachments = runCatching { JSONObject(payload).optJSONArray("attachments") }.getOrNull()
        ?: return emptyList()
    return runCatching { attachments.toAttachmentList().mapNotNull { it.localFilePath } }
        .getOrDefault(emptyList())
}
