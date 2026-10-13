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

    /** Pending rows whose retry window has opened; [now] is epoch millis, null nextAttemptAt is eligible. */
    @Query(
        """
        SELECT * FROM outbox_responses
        WHERE status IN ('QUEUED', 'FAILED')
          AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
        ORDER BY created_at ASC
        """
    )
    suspend fun getPendingResponses(now: Long): List<OutboxEntity>

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

    @Query(
        """
        UPDATE outbox_responses
        SET status = 'SENDING',
            attempt_count = attempt_count + 1,
            updated_at = :now
        WHERE response_id = :responseId
        """
    )
    suspend fun markSending(responseId: String, now: Long)

    @Query(
        """
        UPDATE outbox_responses
        SET status = 'SENT',
            receipt_response_id = :receiptResponseId,
            receipt_accepted_at = :receiptAcceptedAt,
            error_message = NULL,
            updated_at = :now
        WHERE response_id = :responseId
        """
    )
    suspend fun recordReceipt(
        responseId: String,
        receiptResponseId: String,
        receiptAcceptedAt: Long,
        now: Long
    )

    @Query(
        """
        UPDATE outbox_responses
        SET status = 'FAILED',
            terminal_error_code = :errorCode,
            error_message = :errorMessage,
            next_attempt_at = NULL,
            updated_at = :now
        WHERE response_id = :responseId
        """
    )
    suspend fun markTerminal(
        responseId: String,
        errorCode: String,
        errorMessage: String?,
        now: Long
    )

    @Query(
        """
        UPDATE outbox_responses
        SET status = 'FAILED',
            attempt_count = :attemptCount,
            next_attempt_at = :nextAttemptAt,
            error_message = :errorMessage,
            updated_at = :now
        WHERE response_id = :responseId
        """
    )
    suspend fun scheduleRetry(
        responseId: String,
        attemptCount: Int,
        nextAttemptAt: Long,
        errorMessage: String?,
        now: Long
    )

    @Query(
        """
        UPDATE outbox_responses
        SET terminal_error_code = :errorCode,
            updated_at = :now
        WHERE response_id = :responseId
        """
    )
    suspend fun setTerminalError(responseId: String, errorCode: String, now: Long = System.currentTimeMillis())

    @Query("SELECT attempt_count FROM outbox_responses WHERE response_id = :responseId")
    suspend fun getAttemptCount(responseId: String): Int?

    @Delete
    suspend fun deleteResponse(response: OutboxEntity)

    @Query("DELETE FROM outbox_responses WHERE response_id = :responseId")
    suspend fun deleteResponseById(responseId: String)

    /** SIMULATED rows are always clearable; a SENT row is only clearable once its receipt is stored. */
    @Query(
        """
        DELETE FROM outbox_responses
        WHERE status = 'SIMULATED'
           OR (status = 'SENT' AND receipt_response_id IS NOT NULL AND receipt_accepted_at IS NOT NULL)
        """
    )
    suspend fun clearSent()

    @Query("DELETE FROM outbox_responses")
    suspend fun clearAll()
}
