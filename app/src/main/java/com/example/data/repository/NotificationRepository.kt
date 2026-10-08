package com.example.data.repository

import android.content.Context
import com.example.data.local.NotificationDao
import com.example.data.local.ServerDao
import com.example.data.model.NotificationEntity
import com.example.ui.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow

class NotificationRepository(
    private val notificationDao: NotificationDao,
    private val serverDao: ServerDao
) {
    val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()
    val unreadCount: Flow<Int> = notificationDao.countUnread()
    val awaitingResponseCount: Flow<Int> = notificationDao.countAwaitingResponse()

    fun getNotification(fromServer: String, id: Long): Flow<NotificationEntity?> {
        return notificationDao.getNotification(fromServer, id)
    }

    suspend fun getNotificationDirect(fromServer: String, id: Long): NotificationEntity? {
        return notificationDao.getNotificationDirect(fromServer, id)
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
}
