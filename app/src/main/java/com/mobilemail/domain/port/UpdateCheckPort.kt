package com.mobilemail.domain.port

import com.mobilemail.domain.model.UpdateCheckResult

interface UpdateCheckPort {
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult
}
