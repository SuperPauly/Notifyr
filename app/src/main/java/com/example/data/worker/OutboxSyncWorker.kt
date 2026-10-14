package com.example.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.NotifyrApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OutboxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? NotifyrApplication ?: return@withContext Result.failure()

        val engine = OutboxSyncEngine(
            outboxDao = app.database.outboxDao(),
            serverDao = app.database.serverDao(),
            transport = app.responseTransport
        )

        if (engine.runOnce()) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "notifyr_outbox_sync"

        fun enqueue(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            } catch (_: Exception) {
            }
        }
    }
}
