package com.example.data.repository

import android.content.Context
import com.example.data.local.NotificationDao
import com.example.data.local.OutboxDao
import com.example.data.local.ServerDao
import com.example.data.model.ActionItem
import com.example.data.model.ActionKind
import com.example.data.model.NotificationEntity
import com.example.data.model.OutboxEntity
import com.example.data.model.OutboxStatus
import com.example.data.worker.OutboxSyncWorker
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class OutboxRepository(
    private val context: Context,
    private val outboxDao: OutboxDao,
    private val notificationDao: NotificationDao,
    private val serverDao: ServerDao
) {
    val allResponses: Flow<List<OutboxEntity>> = outboxDao.getAllResponses()
    val pendingCount: Flow<Int> = outboxDao.countPending()

    suspend fun queueResponse(
        notification: NotificationEntity,
        action: ActionItem,
        replyText: String? = null
    ): String? {
        // Prevent rapid repeated taps from creating multiple responses to the same notification
        val alreadyCount = outboxDao.countResponsesForNotification(notification.fromServer, notification.id)
        if (alreadyCount > 0 || notification.isResponded || notification.isExpired) {
            return null
        }

        // Button actions omit text. Text replies require non-blank text.
        val sanitizedText = if (action.kind == ActionKind.TEXT_REPLY) {
            val trimmed = replyText?.trim()
            if (trimmed.isNullOrBlank()) return null
            trimmed
        } else {
            null
        }

        val server = serverDao.getServerByServerIdDirect(notification.fromServer)
        val connectionId = server?.connectionId ?: notification.fromServer
        val accountId = server?.accountIdentity

        val responseId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val outboxItem = OutboxEntity(
            responseId = responseId,
            notificationId = notification.id,
            fromServer = notification.fromServer,
            actionId = action.id,
            actionLabel = action.label,
            actionKind = action.kind,
            replyText = sanitizedText,
            connectionId = connectionId,
            accountIdentity = accountId,
            notificationTitle = notification.title,
            fromApp = notification.fromApp,
            status = OutboxStatus.QUEUED,
            isSimulated = true, // Demo responses labelled "Simulated"
            createdAt = now,
            updatedAt = now
        )

        // Store in Room before attempting delivery
        outboxDao.insertResponse(outboxItem)

        // Mark notification as responded in Room
        notificationDao.recordResponse(
            serverId = notification.fromServer,
            id = notification.id,
            actionId = action.id,
            text = sanitizedText ?: action.label,
            respondedAt = now
        )

        // Enqueue WorkManager worker with network constraints to attempt delivery
        OutboxSyncWorker.enqueue(context)

        return responseId
    }

    suspend fun retryResponse(responseId: String) {
        val item = outboxDao.getResponseById(responseId) ?: return
        outboxDao.updateStatus(
            responseId = item.responseId,
            status = OutboxStatus.QUEUED,
            updatedAt = System.currentTimeMillis(),
            errorMessage = null
        )
        OutboxSyncWorker.enqueue(context)
    }

    suspend fun flushPendingOutbox() {
        OutboxSyncWorker.enqueue(context)
    }

    suspend fun deleteResponse(responseId: String) {
        outboxDao.deleteResponseById(responseId)
    }

    suspend fun clearSent() {
        outboxDao.clearSent()
    }

    suspend fun clearDelivered() {
        outboxDao.clearSent()
    }
}
