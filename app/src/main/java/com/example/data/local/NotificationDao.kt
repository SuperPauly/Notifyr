package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications ORDER BY created_at DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE from_server = :serverId ORDER BY created_at DESC")
    fun getNotificationsByServer(serverId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE from_server = :serverId AND id = :id LIMIT 1")
    fun getNotification(serverId: String, id: Long): Flow<NotificationEntity?>

    @Query("SELECT * FROM notifications WHERE from_server = :serverId AND id = :id LIMIT 1")
    suspend fun getNotificationDirect(serverId: String, id: Long): NotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Update
    suspend fun updateNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET is_read = :isRead WHERE from_server = :serverId AND id = :id")
    suspend fun markAsRead(serverId: String, id: Long, isRead: Boolean)

    @Query("UPDATE notifications SET is_read = 1 WHERE (:serverId IS NULL OR from_server = :serverId)")
    suspend fun markAllAsRead(serverId: String?)

    @Query(
        """
        UPDATE notifications 
        SET is_responded = 1, 
            responded_action_id = :actionId, 
            responded_text = :text, 
            responded_at = :respondedAt,
            is_read = 1
        WHERE from_server = :serverId AND id = :id
        """
    )
    suspend fun recordResponse(
        serverId: String,
        id: Long,
        actionId: String,
        text: String?,
        respondedAt: Long
    )

    @Query("DELETE FROM notifications WHERE from_server = :serverId AND id = :id")
    suspend fun deleteNotification(serverId: String, id: Long)

    @Query("DELETE FROM notifications WHERE from_server = :serverId")
    suspend fun deleteNotificationsForServer(serverId: String)

    @Query("DELETE FROM notifications WHERE expires_at IS NOT NULL AND expires_at < :currentTime")
    suspend fun deleteExpiredNotifications(currentTime: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM notifications WHERE is_read = 0")
    fun countUnread(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE is_responded = 0 AND actions != '[]'")
    fun countAwaitingResponse(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getTotalCount(): Int
}
