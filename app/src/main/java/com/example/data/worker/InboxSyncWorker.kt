package com.example.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.BackoffPolicy
import com.example.NotifyrApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Reconciles the inbox for every enabled connection by paging `GET /v1/inbox`.
 * Runs alongside the UnifiedPush receiver without coordination: pushes are incremental and
 * idempotent against the same cursor/revision rules, so a replay from either path is harmless.
 */
class InboxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? NotifyrApplication ?: return@withContext Result.failure()
        val repo = app.inboxRepository
        val servers = app.database.serverDao().getEnabledServersDirect()

        var anyRetryable = false
        for (server in servers) {
            try {
                repo.refresh(server)
            } catch (e: Exception) {
                anyRetryable = true
            }
        }

        if (anyRetryable && runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "notifyr_inbox_sync"
        private const val MAX_ATTEMPTS = 5
        private const val BACKOFF_SECONDS = 30L

        fun enqueue(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<InboxSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        BACKOFF_SECONDS,
                        TimeUnit.SECONDS
                    )
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (_: Exception) {
                // WorkManager may be unavailable in tests without an initializer; sync is best-effort.
            }
        }
    }
}
