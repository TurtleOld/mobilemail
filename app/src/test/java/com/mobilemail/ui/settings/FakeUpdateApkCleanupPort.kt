package com.mobilemail.ui.settings

import com.mobilemail.domain.port.UpdateApkCleanupPort

/**
 * Управляемая тестами реализация [UpdateApkCleanupPort]: признак просроченности
 * задаётся вручную, очищенные пути фиксируются.
 */
class FakeUpdateApkCleanupPort(
    var expired: Boolean = false
) : UpdateApkCleanupPort {
    val cleanedPaths = mutableListOf<String>()

    override suspend fun isExpired(): Boolean = expired

    override suspend fun <T> duringInstallTransfer(block: suspend () -> T): T = block()

    override suspend fun clean(apkFilePath: String) {
        cleanedPaths.add(apkFilePath)
    }
}
