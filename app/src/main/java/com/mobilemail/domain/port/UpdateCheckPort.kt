package com.mobilemail.domain.port

import com.mobilemail.domain.model.UpdateCheckResult

/**
 * Реализации отвечают за собственную потокобезопасность: параллельные вызовы
 * [checkForUpdate] не требуют внешней сериализации со стороны вызывающего.
 */
interface UpdateCheckPort {
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult
}
