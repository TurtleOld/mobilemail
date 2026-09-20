package com.mobilemail.domain.port

/**
 * Планирует фоновую очистку просроченного APK. Выполнение не требует сети и
 * может быть задержано системой; точные будильники не используются.
 */
interface UpdateCleanupSchedulerPort {

    /** Планирует очистку к моменту [completedAtMillis] плюс срок хранения. */
    fun schedule(completedAtMillis: Long)

    /** Отменяет ранее запланированную очистку. */
    fun cancel()
}
