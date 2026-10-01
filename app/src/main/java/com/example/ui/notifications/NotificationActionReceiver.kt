package com.example.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.NotifyrApplication
import com.example.data.model.ActionKind
import com.example.data.model.OutboxEntity
import com.example.data.model.OutboxStatus
import com.example.data.worker.OutboxSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Non-exported BroadcastReceiver capturing notification action button clicks
 * and Android RemoteInput text replies directly from the system notification shade.
 * Never performs network operations directly; persists records to Room and enqueues OutboxSyncWorker.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val action = intent.action ?: return
        if (action != ACTION_BUTTON_CLICK && action != ACTION_TEXT_REPLY) return

        val fromServer = intent.getStringExtra(NotificationHelper.EXTRA_FROM_SERVER) ?: return
        val notificationId = intent.getLongExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1L)
        if (notificationId == -1L) return

        val connectionId = intent.getStringExtra(NotificationHelper.EXTRA_CONNECTION_ID) ?: fromServer
        val actionId = intent.getStringExtra(NotificationHelper.EXTRA_ACTION_ID) ?: ""
        val actionLabel = intent.getStringExtra(NotificationHelper.EXTRA_ACTION_LABEL) ?: ""
        val kindStr = intent.getStringExtra(NotificationHelper.EXTRA_ACTION_KIND) ?: ActionKind.BUTTON.name
        val title = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_TITLE) ?: "Notification"
        val fromApp = intent.getStringExtra(NotificationHelper.EXTRA_FROM_APP) ?: "app"

        val actionKind = try {
            ActionKind.valueOf(kindStr)
        } catch (_: Exception) {
            ActionKind.BUTTON
        }

        val replyText = if (actionKind == ActionKind.TEXT_REPLY) {
            val remoteInputResults = RemoteInput.getResultsFromIntent(intent)
            val text = remoteInputResults?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()?.trim()
            if (text.isNullOrBlank()) return // Ignore blank text replies
            text
        } else {
            null // Button actions omit text
        }

        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                val app = context.applicationContext as? NotifyrApplication ?: return@launch
                val db = app.database
                val notifDao = db.notificationDao()
                val outboxDao = db.outboxDao()

                // Check if already responded or expired to prevent invalid responses
                val alreadyCount = outboxDao.countResponsesForNotification(fromServer, notificationId)
                if (alreadyCount > 0) {
                    NotificationHelper.dismissNotification(context, fromServer, notificationId)
                    return@launch
                }

                val existingNotification = notifDao.getNotificationDirect(fromServer, notificationId)
                if (existingNotification == null || existingNotification.isResponded || existingNotification.isExpired) {
                    NotificationHelper.dismissNotification(context, fromServer, notificationId)
                    return@launch
                }

                val server = db.serverDao().getServerByConnectionIdDirect(connectionId)
                    ?: db.serverDao().getServerByServerIdDirect(fromServer)
                val accountIdentity = server?.accountIdentity

                val now = System.currentTimeMillis()
                val responseId = UUID.randomUUID().toString()

                val outboxEntity = OutboxEntity(
                    responseId = responseId,
                    notificationId = notificationId,
                    fromServer = fromServer,
                    actionId = actionId,
                    actionLabel = actionLabel,
                    actionKind = actionKind,
                    replyText = replyText,
                    connectionId = connectionId,
                    accountIdentity = accountIdentity,
                    notificationTitle = title,
                    fromApp = fromApp,
                    status = OutboxStatus.QUEUED,
                    isSimulated = true, // Labelled as Simulated for demo pipeline
                    createdAt = now,
                    updatedAt = now
                )

                // Persist response into Outbox Room database
                outboxDao.insertResponse(outboxEntity)

                // Update notification state locally
                notifDao.recordResponse(
                    serverId = fromServer,
                    id = notificationId,
                    actionId = actionId,
                    text = replyText ?: actionLabel,
                    respondedAt = now
                )

                // Dismiss or update the system notification
                NotificationHelper.dismissNotification(context, fromServer, notificationId)

                // Enqueue background sync worker with network constraints
                OutboxSyncWorker.enqueue(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_BUTTON_CLICK = "com.example.notifyr.ACTION_BUTTON_CLICK"
        const val ACTION_TEXT_REPLY = "com.example.notifyr.ACTION_TEXT_REPLY"
    }
}
