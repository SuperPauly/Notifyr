package com.example.data.unifiedpush

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.NotifyrApplication
import com.example.ui.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver implementing the client side of the UnifiedPush Android connector.
 * Receives NEW_ENDPOINT, UNREGISTERED, REGISTRATION_FAILED, and MESSAGE broadcasts.
 */
class UnifiedPushReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val token = intent.getStringExtra(UnifiedPushConstants.EXTRA_TOKEN) ?: return

        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                val app = context.applicationContext as? NotifyrApplication ?: return@launch
                val serverDao = app.database.serverDao()
                val notificationDao = app.database.notificationDao()

                val server = serverDao.getServerByConnectionIdDirect(token) ?: return@launch

                when (action) {
                    UnifiedPushConstants.ACTION_NEW_ENDPOINT -> {
                        val endpoint = intent.getStringExtra(UnifiedPushConstants.EXTRA_ENDPOINT)
                        serverDao.updateUnifiedPushState(
                            connectionId = token,
                            distributor = server.upDistributor,
                            endpoint = endpoint,
                            status = UnifiedPushConstants.STATUS_REGISTERED_AWAITING_SERVER
                        )
                    }

                    UnifiedPushConstants.ACTION_UNREGISTERED -> {
                        serverDao.updateUnifiedPushState(
                            connectionId = token,
                            distributor = null,
                            endpoint = null,
                            status = UnifiedPushConstants.STATUS_UNREGISTERED
                        )
                    }

                    UnifiedPushConstants.ACTION_REGISTRATION_FAILED -> {
                        val reason = intent.getStringExtra(UnifiedPushConstants.EXTRA_REASON) ?: "Unknown"
                        serverDao.updateUnifiedPushState(
                            connectionId = token,
                            distributor = server.upDistributor,
                            endpoint = null,
                            status = "Registration Failed: $reason"
                        )
                    }

                    UnifiedPushConstants.ACTION_MESSAGE -> {
                        val rawBytes = intent.getByteArrayExtra(UnifiedPushConstants.EXTRA_BYTES_MESSAGE)
                        val rawString = intent.getStringExtra(UnifiedPushConstants.EXTRA_MESSAGE)

                        val notification = UnifiedPushPayloadDecoder.decodePayload(
                            rawBytes = rawBytes,
                            rawString = rawString,
                            server = server
                        )

                        if (notification != null) {
                            // Persist before posting notification
                            notificationDao.insertNotification(notification)

                            // Post Android system notification with RemoteInput and actions
                            NotificationHelper.postNotification(
                                context = context,
                                notification = notification,
                                connectionId = server.connectionId
                            )
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
