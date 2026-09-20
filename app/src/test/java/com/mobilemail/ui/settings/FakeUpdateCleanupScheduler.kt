package com.mobilemail.ui.settings

import com.mobilemail.domain.port.UpdateCleanupSchedulerPort

/**
 * Управляемая тестами реализация [UpdateCleanupSchedulerPort]: фиксирует
 * запланированное время завершения и отмены.
 */
class FakeUpdateCleanupScheduler : UpdateCleanupSchedulerPort {
    val scheduledCompletedAt = mutableListOf<Long>()
    var cancelCount = 0
        private set

    override fun schedule(completedAtMillis: Long) {
        scheduledCompletedAt.add(completedAtMillis)
    }

    override fun cancel() {
        cancelCount++
    }
}
