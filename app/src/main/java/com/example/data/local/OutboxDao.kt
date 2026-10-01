package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.OutboxEntity
import com.example.data.model.OutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Query("SELECT * FROM outbox_responses ORDER BY created_at DESC")
    fun getAllResponses(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox_responses WHERE status = :status ORDER BY created_at ASC")
    fun getResponsesByStatus(status: OutboxStatus): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox_responses WHERE status = 'QUEUED' OR status = 'FAILED' ORDER BY created_at ASC")
    suspend fun getPendingResponses(): List<OutboxEntity>

    @Query("SELECT * FROM outbox_responses WHERE response_id = :responseId LIMIT 1")
    suspend fun getResponseById(responseId: String): OutboxEntity?

    @Query("SELECT COUNT(*) FROM outbox_responses WHERE from_server = :fromServer AND notification_id = :notificationId")
    suspend fun countResponsesForNotification(fromServer: String, notificationId: Long): Int

    @Query("SELECT COUNT(*) FROM outbox_responses WHERE status = 'QUEUED' OR status = 'SENDING'")
    fun countPending(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponse(response: OutboxEntity)

    @Update
    suspend fun updateResponse(response: OutboxEntity)

    @Query("UPDATE outbox_responses SET status = :status, updated_at = :updatedAt, error_message = :errorMessage WHERE response_id = :responseId")
    suspend fun updateStatus(
        responseId: String,
        status: OutboxStatus,
        updatedAt: Long,
        errorMessage: String? = null
    )

    @Delete
    suspend fun deleteResponse(response: OutboxEntity)

    @Query("DELETE FROM outbox_responses WHERE response_id = :responseId")
    suspend fun deleteResponseById(responseId: String)

    @Query("DELETE FROM outbox_responses WHERE status = 'SENT' OR status = 'SIMULATED'")
    suspend fun clearSent()

    @Query("DELETE FROM outbox_responses")
    suspend fun clearAll()
}
