package com.mobilemail.data.sync

import com.mobilemail.data.model.Attachment
import com.mobilemail.data.repository.ComposeRepository
import com.mobilemail.domain.common.Result
import com.mobilemail.domain.common.fold
import com.mobilemail.domain.model.toDomain
import org.json.JSONArray
import org.json.JSONObject

/**
 * Выполняет задания очереди типа [OfflineQueueManager.TYPE_SEND].
 *
 * Отдельный объект: отправка требует разбора payload и загрузки вложений, а менеджер
 * очереди отвечает только за статусы заданий.
 */
internal object OfflineQueueSendExecutor {

    /**
     * Отправляет письмо из задания очереди.
     *
     * @param onBeforeSubmission вызывается непосредственно перед запросом EmailSubmission/set,
     *   чтобы завершение процесса после этого момента не привело к слепой повторной отправке.
     */
    suspend fun execute(
        repository: ComposeRepository,
        payload: String,
        fromEmail: String,
        onBeforeSubmission: suspend () -> Unit
    ): Result<Unit> {
        val json = JSONObject(payload)
        val to = json.getJSONArray("to").toStringList()
        val sourceAttachments = attachments(payload)
        val attachments: List<com.mobilemail.domain.model.Attachment> = sourceAttachments
            .map { attachment ->
                if (attachment.isUploaded) {
                    attachment.toDomain()
                } else {
                    val bytes = OfflineAttachmentStorage.read(requireNotNull(attachment.localFilePath))
                    repository.uploadAttachment(bytes, attachment.mime, attachment.filename).fold(
                        onError = { throw it },
                        onSuccess = { uploaded -> uploaded }
                    )
                }
            }
        return repository.sendMessage(
            from = fromEmail,
            to = to,
            subject = json.optString("subject"),
            body = json.optString("body"),
            attachments = attachments,
            draftId = json.optString("draftId").takeIf { it.isNotBlank() && it != "null" },
            onBeforeSubmission = onBeforeSubmission
        ).fold(
            onError = { Result.Error(it) },
            onSuccess = {
                sourceAttachments.forEach { OfflineAttachmentStorage.delete(it.localFilePath) }
                Result.Success(Unit)
            }
        )
    }

    /**
     * Возвращает вложения из payload задания.
     *
     * @param payload JSON задания отправки.
     * @return Вложения в порядке payload; пустой список, если их нет.
     */
    fun attachments(payload: String): List<Attachment> {
        return JSONObject(payload).optJSONArray("attachments")?.toAttachmentList() ?: emptyList()
    }

    private fun JSONArray.toStringList(): List<String> {
        return buildList {
            for (index in 0 until length()) add(getString(index))
        }
    }

    private fun JSONArray.toAttachmentList(): List<Attachment> {
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
}
