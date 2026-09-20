package com.mobilemail.data.update

/**
 * Срок годности неустановленного APK с момента фактического завершения
 * скачивания: ровно 7 суток.
 */
const val APK_EXPIRY_MILLIS = 7L * 24L * 60L * 60L * 1000L

/**
 * Момент, когда скачанный APK перестаёт быть пригодным для установки.
 */
fun apkExpiryDeadlineMillis(completedAtMillis: Long): Long =
    completedAtMillis + APK_EXPIRY_MILLIS

/**
 * Истёк ли срок хранения APK к моменту [nowMillis].
 *
 * [completedAtMillis] равен `null`, пока загрузка не завершалась: срок в этом
 * случае неизвестен, и APK не считается просроченным. Срок достигается ровно
 * на границе семи суток.
 */
fun isApkExpired(completedAtMillis: Long?, nowMillis: Long): Boolean =
    completedAtMillis != null && nowMillis >= apkExpiryDeadlineMillis(completedAtMillis)

/** Задержка до момента, когда скачанный APK станет просроченным. */
fun updateCleanupDelayMillis(completedAtMillis: Long, nowMillis: Long): Long =
    (apkExpiryDeadlineMillis(completedAtMillis) - nowMillis).coerceAtLeast(0L)
