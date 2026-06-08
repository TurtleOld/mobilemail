package com.mobilemail.data.sync

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.data.jmap.OAuthTokenExpiredException
import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.domain.model.FolderRole

data class MailSyncSummary(
    val syncedCount: Int,
    val failedCount: Int,
    val skippedCount: Int
)

class MailSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val summary = syncSavedAccounts(applicationContext as Application)
        return resolveResult(summary)
    }

    private suspend fun syncSavedAccounts(application: Application): MailSyncSummary {
        val preferencesManager = PreferencesManager(application)
        val database = AppDatabase.getInstance(application)
        return preferencesManager.getSavedAccounts()
            .fold(MailSyncSummary(syncedCount = 0, failedCount = 0, skippedCount = 0)) { summary, session ->
                summary + syncSession(application, database, session)
            }
    }

    private suspend fun syncSession(
        application: Application,
        database: AppDatabase,
        session: SavedSession
    ): MailSyncSummary {
        return try {
            val client = MailClientFactory.create(
                application = application,
                server = session.server,
                email = session.email,
                accountId = session.accountId
            )
            val repository = MailRepository(
                jmapClient = client,
                messageDao = database.messageDao(),
                folderDao = database.folderDao()
            )
            val inbox = repository.getFolders()
                .getOrThrow()
                .firstOrNull { it.role == FolderRole.INBOX }
                ?: return MailSyncSummary(syncedCount = 0, failedCount = 0, skippedCount = 1)

            repository.getMessagesPage(
                folderId = inbox.id,
                position = 0,
                limit = MailSyncWorkPolicy.PAGE_SIZE,
                forceRefresh = true
            ).getOrThrow()
            MailSyncSummary(syncedCount = 1, failedCount = 0, skippedCount = 0)
        } catch (error: OAuthTokenExpiredException) {
            Log.w("MailSyncWorker", "Skipping mail sync for expired session")
            MailSyncSummary(syncedCount = 0, failedCount = 0, skippedCount = 1)
        } catch (error: Exception) {
            Log.e("MailSyncWorker", "Failed to sync mail", error)
            MailSyncSummary(syncedCount = 0, failedCount = 1, skippedCount = 0)
        }
    }

    companion object {
        internal fun resolveResult(summary: MailSyncSummary): Result {
            return if (summary.failedCount > 0) Result.retry() else Result.success()
        }

        fun scheduleNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                MailSyncWorkPolicy.UNIQUE_ONE_TIME,
                MailSyncWorkPolicy.oneTimeExistingWorkPolicy(),
                MailSyncWorkPolicy.buildOneTimeWorkRequest()
            )
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                MailSyncWorkPolicy.UNIQUE_PERIODIC,
                MailSyncWorkPolicy.periodicExistingWorkPolicy(),
                MailSyncWorkPolicy.buildPeriodicWorkRequest()
            )
        }
    }
}

private operator fun MailSyncSummary.plus(other: MailSyncSummary): MailSyncSummary {
    return MailSyncSummary(
        syncedCount = syncedCount + other.syncedCount,
        failedCount = failedCount + other.failedCount,
        skippedCount = skippedCount + other.skippedCount
    )
}
