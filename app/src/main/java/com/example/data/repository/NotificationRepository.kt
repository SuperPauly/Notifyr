package com.example.data.repository

import android.content.Context
import com.example.data.local.NotificationDao
import com.example.data.local.ServerDao
import com.example.data.model.ActionItem
import com.example.data.model.ActionKind
import com.example.data.model.NotificationEntity
import com.example.data.transport.DemoNotificationTransport
import com.example.ui.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicLong

enum class DemoNotificationType {
    ORDINARY,
    BUTTON_ACTION,
    TEXT_REPLY,
    EXPIRED
}

class NotificationRepository(
    private val notificationDao: NotificationDao,
    private val serverDao: ServerDao,
    private val transport: DemoNotificationTransport
) {
    private val demoIdCounter = AtomicLong(800)

    val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()
    val unreadCount: Flow<Int> = notificationDao.countUnread()
    val awaitingResponseCount: Flow<Int> = notificationDao.countAwaitingResponse()

    fun getNotification(fromServer: String, id: Long): Flow<NotificationEntity?> {
        return notificationDao.getNotification(fromServer, id)
    }

    suspend fun getNotificationDirect(fromServer: String, id: Long): NotificationEntity? {
        return notificationDao.getNotificationDirect(fromServer, id)
    }

    suspend fun ensureInitialData() {
        val total = notificationDao.getTotalCount()
        if (total == 0) {
            val initial = transport.getInitialDemoNotifications()
            notificationDao.insertNotifications(initial)
        }
    }

    suspend fun reloadDemoData() {
        notificationDao.clearAll()
        val initial = transport.getInitialDemoNotifications()
        notificationDao.insertNotifications(initial)
    }

    /**
     * Re-fetches or synchronizes the notification list.
     */
    suspend fun refreshNotifications() {
        kotlinx.coroutines.delay(400)
    }

    /**
     * Persists an incoming message before posting its notification.
     * Duplicate delivery updates the existing record without creating repeated alerts.
     */
    suspend fun processIncomingNotification(
        context: Context,
        notification: NotificationEntity,
        shouldAlert: Boolean = true
    ): NotificationEntity {
        val existing = notificationDao.getNotificationDirect(notification.fromServer, notification.id)
        val isDuplicate = existing != null &&
                existing.title == notification.title &&
                existing.body == notification.body &&
                existing.actions == notification.actions

        // Persist into Room
        notificationDao.insertNotification(notification)

        // Only post alert if not a duplicate repeat
        if (shouldAlert && !isDuplicate) {
            val server = serverDao.getServerByServerIdDirect(notification.fromServer)
            val connectionId = server?.connectionId ?: notification.fromServer
            NotificationHelper.postNotification(context, notification, connectionId)
        }

        return notification
    }

    /**
     * Demo testing tool that posts each supported notification type
     * through the exact same system notification pipeline with RemoteInput and actions.
     */
    suspend fun postDemoNotification(context: Context, type: DemoNotificationType): NotificationEntity {
        val newId = demoIdCounter.incrementAndGet()
        val now = System.currentTimeMillis()

        val notification = when (type) {
            DemoNotificationType.ORDINARY -> NotificationEntity(
                id = newId,
                createdAt = now,
                fromServer = "srv-prod-us",
                fromApp = "storage-backup",
                title = "Backup Snapshot #$newId Complete",
                body = "Volume snapshot for PostgreSQL primary node finished in 42s. 18.2 GB archived to cold storage.",
                actions = emptyList(),
                expiresAt = null,
                openPath = "/backups/snapshots/$newId",
                isRead = false,
                isResponded = false
            )

            DemoNotificationType.BUTTON_ACTION -> NotificationEntity(
                id = newId,
                createdAt = now,
                fromServer = "srv-prod-us",
                fromApp = "auth-gate",
                title = "Break-Glass IAM Request #$newId",
                body = "Engineer sarah@corp.io requested temporary sudo role on cluster prod-us-1. Tap button to approve or deny.",
                actions = listOf(
                    ActionItem("btn_approve_$newId", "Approve", ActionKind.BUTTON),
                    ActionItem("btn_deny_$newId", "Deny", ActionKind.BUTTON)
                ),
                expiresAt = now + (30 * 60 * 1000), // 30m window
                openPath = "/iam/requests/$newId",
                isRead = false,
                isResponded = false
            )

            DemoNotificationType.TEXT_REPLY -> NotificationEntity(
                id = newId,
                createdAt = now,
                fromServer = "srv-staging-eu",
                fromApp = "incident-bot",
                title = "Sev-2 Triage Requested #$newId",
                body = "504 Gateway errors observed on EU payment gateway. Type your triage update or mitigation runbook command directly in this notification.",
                actions = listOf(
                    ActionItem("reply_triage_$newId", "Type Triage Note", ActionKind.TEXT_REPLY)
                ),
                expiresAt = now + (60 * 60 * 1000),
                openPath = "/incidents/$newId",
                isRead = false,
                isResponded = false
            )

            DemoNotificationType.EXPIRED -> NotificationEntity(
                id = newId,
                createdAt = now - (20 * 60 * 1000),
                fromServer = "srv-staging-eu",
                fromApp = "security-otp",
                title = "Expired OTP Challenge #$newId",
                body = "One-time approval challenge #$newId expired 5 minutes ago. Action controls are disabled.",
                actions = listOf(
                    ActionItem("btn_otp_$newId", "Validate OTP", ActionKind.BUTTON)
                ),
                expiresAt = now - (5 * 60 * 1000), // Expired in past
                openPath = null,
                isRead = false,
                isResponded = false
            )
        }

        return processIncomingNotification(context, notification, shouldAlert = true)
    }

    suspend fun markAsRead(fromServer: String, id: Long, isRead: Boolean) {
        notificationDao.markAsRead(fromServer, id, isRead)
    }

    suspend fun markAllAsRead(fromServer: String? = null) {
        notificationDao.markAllAsRead(fromServer)
    }

    suspend fun deleteNotification(fromServer: String, id: Long) {
        notificationDao.deleteNotification(fromServer, id)
    }

    suspend fun cleanExpired() {
        notificationDao.deleteExpiredNotifications(System.currentTimeMillis())
    }

    suspend fun simulateIncomingNotification(): NotificationEntity? {
        val servers = serverDao.getServerByServerIdDirect("srv-prod-us")
            ?: serverDao.getServerByServerIdDirect("srv-staging-eu")
        if (servers != null) {
            val newNotif = transport.generateSimulatedNotification(servers)
            notificationDao.insertNotification(newNotif)
            return newNotif
        }
        return null
    }
}
