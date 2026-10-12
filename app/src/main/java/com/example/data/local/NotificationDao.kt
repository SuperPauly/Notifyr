package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Highest stored revision for one server, compared as a zero-padded-ish decimal string.
     * Returns null when the server has no rows. `revision` values are fixed-width decimal
     * strings from the server, so lexicographic MAX equals numeric MAX; the Kotlin-side
     * comparator in [com.example.data.repository.InboxRepository] is authoritative.
     */
    @Query("SELECT MAX(revision) FROM notifications WHERE from_server = :serverId")
    suspend fun highestRevision(serverId: String): String?

    @Query("SELECT revision FROM notifications WHERE from_server = :serverId AND id = :id LIMIT 1")
    suspend fun revisionOf(serverId: String, id: Long): String?

    /**
     * Writes one inbox page and advances the connection cursor in a single transaction.
     * If any write throws, both the page and the cursor roll back together. A null
     * [connectionId] writes the page without touching any cursor.
     */
    @Transaction
    suspend fun applyPageAndAdvanceCursor(
        connectionId: String?,
        items: List<NotificationEntity>,
        cursor: String
    ) {
        insertNotifications(items)
        if (connectionId != null) updateCursor(connectionId, cursor)
    }

    @Query("UPDATE servers SET inbox_cursor = :cursor WHERE connection_id = :connectionId")
    suspend fun updateCursor(connectionId: String, cursor: String)
}
