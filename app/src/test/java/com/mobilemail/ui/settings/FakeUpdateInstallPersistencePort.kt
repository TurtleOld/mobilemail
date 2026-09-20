package com.mobilemail.ui.settings

import com.mobilemail.domain.port.PendingInstall
import com.mobilemail.domain.port.UpdateInstallPersistencePort

class FakeUpdateInstallPersistencePort(
    private var pending: PendingInstall? = null
) : UpdateInstallPersistencePort {
    var clearCalls = 0
        private set

    override suspend fun savePendingInstall(pending: PendingInstall) {
        this.pending = pending
    }

    override suspend fun loadPendingInstall(): PendingInstall? = pending

    override suspend fun clear() {
        pending = null
        clearCalls++
    }
}
