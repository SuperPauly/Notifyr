package com.example.data.worker

import com.example.data.local.OutboxDao
import com.example.data.local.ServerDao
import com.example.data.model.OutboxStatus
import com.example.data.response.ErrorClassifier
import com.example.data.transport.NotificationResponseTransport
import com.example.data.transport.ResponseDeliveryException

/**
 * One delivery pass over the outbox, extracted from [OutboxSyncWorker] so the receipt,
 * retry and terminal-error rules run against an in-memory database and a fake transport.
 *
 * Per row: the persisted `response_id` and payload are reused verbatim across attempts;
 * a receipt is written atomically with SENT (never SENT without a receipt); `Retry-After`
 * and the persisted `attempt_count` drive the next attempt; a terminal classification
 * ([OutboxDao.markTerminal]) removes the row from future pending queries.
 */
class OutboxSyncEngine(
    private val outboxDao: OutboxDao,
    private val serverDao: ServerDao,
    private val transport: NotificationResponseTransport,
    private val now: () -> Long = System::currentTimeMillis
) {

    /** Runs one pass; returns `true` when at least one row must be attempted again. */
    suspend fun runOnce(): Boolean {
        val pending = outboxDao.getPendingResponses()
        var anyFailed = false

        for (item in pending) {
            val current = now()

            if (item.nextAttemptAt != null && item.nextAttemptAt > current) {
                continue
            }

            outboxDao.updateStatus(item.responseId, OutboxStatus.SENDING, current)

            val server = serverDao.getServerByConnectionIdDirect(item.connectionId)
                ?: serverDao.getServerByServerIdDirect(item.fromServer)

            if (server == null) {
                outboxDao.updateStatus(
                    responseId = item.responseId,
                    status = OutboxStatus.FAILED,
                    updatedAt = current,
                    errorMessage = "Server profile for '${item.fromServer}' was removed or unconfigured"
                )
                anyFailed = true
                continue
            }

            if (!server.isEnabled) {
                outboxDao.updateStatus(
                    responseId = item.responseId,
                    status = OutboxStatus.AUTH_REQUIRED,
                    updatedAt = current,
                    errorMessage = "Server connection is disabled"
                )
                anyFailed = true
                continue
            }

            val deliveryResult = transport.deliverResponse(server, item)
            if (deliveryResult.isSuccess) {
                val receipt = deliveryResult.getOrNull()
                if (item.isSimulated) {
                    outboxDao.updateStatus(
                        responseId = item.responseId,
                        status = OutboxStatus.SIMULATED,
                        updatedAt = current,
                        errorMessage = null
                    )
                } else if (receipt != null) {
                    outboxDao.recordReceipt(
                        responseId = item.responseId,
                        receiptResponseId = receipt.responseId,
                        receiptAcceptedAt = java.time.Instant.parse(receipt.acceptedAt).toEpochMilli(),
                        now = current
                    )
                } else {
                    outboxDao.updateStatus(
                        responseId = item.responseId,
                        status = OutboxStatus.SENT,
                        updatedAt = current,
                        errorMessage = null
                    )
                }
            } else {
                val exception = deliveryResult.exceptionOrNull()
                val deliveryException = exception as? ResponseDeliveryException
                val httpCode = deliveryException?.code ?: 0

                if (httpCode == 410 && item.receiptResponseId != null) {
                    outboxDao.recordReceipt(
                        responseId = item.responseId,
                        receiptResponseId = item.receiptResponseId,
                        receiptAcceptedAt = item.receiptAcceptedAt ?: current,
                        now = current
                    )
                } else {
                    val classification = ErrorClassifier.classify(httpCode, deliveryException?.response)

                    if (classification.isTerminal) {
                        outboxDao.markTerminal(
                            responseId = item.responseId,
                            errorCode = classification.errorCode ?: "unknown",
                            errorMessage = exception?.message ?: "Transmission rejected",
                            now = current
                        )
                    } else if (classification.isRetryable) {
                        val attemptCount = item.attemptCount + 1
                        val backoffMs = ErrorClassifier.calculateBackoff(attemptCount, classification.retryAfterMs)
                        val nextAttempt = current + backoffMs

                        outboxDao.scheduleRetry(
                            responseId = item.responseId,
                            attemptCount = attemptCount,
                            nextAttemptAt = nextAttempt,
                            errorMessage = exception?.message,
                            now = current
                        )
                        anyFailed = true
                    }
                }
            }
        }

        return anyFailed
    }
}
