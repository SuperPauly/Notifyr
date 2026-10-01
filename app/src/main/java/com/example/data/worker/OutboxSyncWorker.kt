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
import com.example.data.model.OutboxStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OutboxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? NotifyrApplication ?: return@withContext Result.failure()
        val outboxDao = app.database.outboxDao()
        val serverDao = app.database.serverDao()
        val transport = app.transport

        val pending = outboxDao.getPendingResponses()
        if (pending.isEmpty()) {
            return@withContext Result.success()
        }

        var anyFailed = false

        for (item in pending) {
            val now = System.currentTimeMillis()
            outboxDao.updateStatus(item.responseId, OutboxStatus.SENDING, now)

            // Look up server by connectionId first, then by serverId
            val server = serverDao.getServerByConnectionIdDirect(item.connectionId)
                ?: serverDao.getServerByServerIdDirect(item.fromServer)

            if (server == null) {
                // Server profile removed or not found
                outboxDao.updateStatus(
                    responseId = item.responseId,
                    status = OutboxStatus.FAILED,
                    updatedAt = System.currentTimeMillis(),
                    errorMessage = "Server profile for '${item.fromServer}' was removed or unconfigured"
                )
                anyFailed = true
                continue
            }

            if (!server.isEnabled) {
                outboxDao.updateStatus(
                    responseId = item.responseId,
                    status = OutboxStatus.AUTH_REQUIRED,
                    updatedAt = System.currentTimeMillis(),
                    errorMessage = "Server connection is disabled"
                )
                anyFailed = true
                continue
            }

            val deliveryResult = transport.deliverResponse(server, item)
            if (deliveryResult.isSuccess) {
                val finalStatus = if (item.isSimulated) OutboxStatus.SIMULATED else OutboxStatus.SENT
                outboxDao.updateStatus(
                    responseId = item.responseId,
                    status = finalStatus,
                    updatedAt = System.currentTimeMillis(),
                    errorMessage = null
                )
            } else {
                outboxDao.updateStatus(
                    responseId = item.responseId,
                    status = OutboxStatus.FAILED,
                    updatedAt = System.currentTimeMillis(),
                    errorMessage = deliveryResult.exceptionOrNull()?.message ?: "Transmission rejected"
                )
                anyFailed = true
            }
        }

        if (anyFailed && runAttemptCount < 3) {
            Result.retry()
        } else {
            Result.success()
        }
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
                // Graceful fallback for JVM test environments where WorkManager is not initialized
            }
        }
    }
}
