package com.example.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.R
import com.example.data.model.ActionKind
import com.example.data.model.NotificationEntity

object NotificationHelper {

    const val CHANNEL_ALERTS_ID = "notifyr_alerts_channel"
    const val CHANNEL_INFO_ID = "notifyr_info_channel"

    const val EXTRA_FROM_SERVER = "extra_from_server"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_CONNECTION_ID = "extra_connection_id"
    const val EXTRA_ACTION_ID = "extra_action_id"
    const val EXTRA_ACTION_LABEL = "extra_action_label"
    const val EXTRA_ACTION_KIND = "extra_action_kind"
    const val EXTRA_NOTIFICATION_TITLE = "extra_notification_title"
    const val EXTRA_FROM_APP = "extra_from_app"

    const val KEY_TEXT_REPLY = "key_notifyr_text_reply"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Notifyr Interactive Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts requiring interactive approval, input, or triage"
                enableVibration(true)
                setShowBadge(true)
            }

            val infoChannel = NotificationChannel(
                CHANNEL_INFO_ID,
                "Notifyr Informational Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Routine status notifications and logs"
                enableVibration(false)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(infoChannel)
        }
    }

    fun postNotification(
        context: Context,
        notification: NotificationEntity,
        connectionId: String = notification.fromServer
    ) {
        createNotificationChannels(context)

        val channelId = if (notification.actions.isNotEmpty() && !notification.isExpired) {
            CHANNEL_ALERTS_ID
        } else {
            CHANNEL_INFO_ID
        }

        // Tap notification intent -> Opens MainActivity with detail navigation
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_SERVER, notification.fromServer)
            putExtra(EXTRA_NOTIFICATION_ID, notification.id)
        }

        val tapPendingIntent = PendingIntent.getActivity(
            context,
            generateRequestCode(notification.fromServer, notification.id, "tap"),
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setSubText("${notification.fromServer} • ${notification.fromApp}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .setPriority(
                if (notification.actions.isNotEmpty() && !notification.isExpired) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )

        // Only attach action controls if notification is neither expired nor responded
        if (!notification.isResponded && !notification.isExpired && notification.actions.isNotEmpty()) {
            // Android allows up to 3 visible action buttons in notification shade
            val visibleActions = notification.actions.take(3)
            for (item in visibleActions) {
                if (item.kind == ActionKind.TEXT_REPLY) {
                    val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                        .setLabel(item.label)
                        .build()

                    val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        this.action = NotificationActionReceiver.ACTION_TEXT_REPLY
                        putExtra(EXTRA_FROM_SERVER, notification.fromServer)
                        putExtra(EXTRA_NOTIFICATION_ID, notification.id)
                        putExtra(EXTRA_CONNECTION_ID, connectionId)
                        putExtra(EXTRA_ACTION_ID, item.id)
                        putExtra(EXTRA_ACTION_LABEL, item.label)
                        putExtra(EXTRA_ACTION_KIND, item.kind.name)
                        putExtra(EXTRA_NOTIFICATION_TITLE, notification.title)
                        putExtra(EXTRA_FROM_APP, notification.fromApp)
                    }

                    // RemoteInput requires FLAG_MUTABLE
                    val replyPendingIntent = PendingIntent.getBroadcast(
                        context,
                        generateRequestCode(notification.fromServer, notification.id, item.id),
                        replyIntent,
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val actionCompat = NotificationCompat.Action.Builder(
                        R.drawable.ic_launcher_foreground,
                        item.label,
                        replyPendingIntent
                    )
                        .addRemoteInput(remoteInput)
                        .build()

                    builder.addAction(actionCompat)
                } else {
                    // Regular BUTTON action
                    val btnIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        this.action = NotificationActionReceiver.ACTION_BUTTON_CLICK
                        putExtra(EXTRA_FROM_SERVER, notification.fromServer)
                        putExtra(EXTRA_NOTIFICATION_ID, notification.id)
                        putExtra(EXTRA_CONNECTION_ID, connectionId)
                        putExtra(EXTRA_ACTION_ID, item.id)
                        putExtra(EXTRA_ACTION_LABEL, item.label)
                        putExtra(EXTRA_ACTION_KIND, item.kind.name)
                        putExtra(EXTRA_NOTIFICATION_TITLE, notification.title)
                        putExtra(EXTRA_FROM_APP, notification.fromApp)
                    }

                    // Button actions use FLAG_IMMUTABLE
                    val btnPendingIntent = PendingIntent.getBroadcast(
                        context,
                        generateRequestCode(notification.fromServer, notification.id, item.id),
                        btnIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val actionCompat = NotificationCompat.Action.Builder(
                        R.drawable.ic_launcher_foreground,
                        item.label,
                        btnPendingIntent
                    ).build()

                    builder.addAction(actionCompat)
                }
            }
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            val notificationTag = getNotificationTag(notification.fromServer, notification.id)
            val notificationIdInt = getNotificationId(notification.fromServer, notification.id)
            notificationManager.notify(notificationTag, notificationIdInt, builder.build())
        } catch (_: SecurityException) {
            // Permission denied
        }
    }

    fun dismissNotification(context: Context, fromServer: String, id: Long) {
        val notificationManager = NotificationManagerCompat.from(context)
        val tag = getNotificationTag(fromServer, id)
        val notificationIdInt = getNotificationId(fromServer, id)
        notificationManager.cancel(tag, notificationIdInt)
    }

    fun getNotificationTag(fromServer: String, id: Long): String {
        return "${fromServer}_$id"
    }

    fun getNotificationId(fromServer: String, id: Long): Int {
        return (getNotificationTag(fromServer, id).hashCode() and 0x7FFFFFFF)
    }

    private fun generateRequestCode(fromServer: String, id: Long, actionKey: String): Int {
        return ("${fromServer}_${id}_${actionKey}".hashCode() and 0x7FFFFFFF)
    }
}
