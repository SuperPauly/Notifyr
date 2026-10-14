package com.example.data.worker

import com.example.data.local.OutboxDao
import com.example.data.local.ServerDao
import com.example.data.model.OutboxEntity
import com.example.data.model.OutboxStatus
import com.example.data.response.ErrorClassifier
import com.example.data.transport.NotificationResponseTransport
import com.example.data.transport.ResponseDeliveryException
import java.time.Instant

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

            anyFailed = try {
                processRow(item, current) || anyFailed
            } catch (e: Exception) {
                // A transport or receipt-parse exception must not leave the row stuck in SENDING.
                scheduleRetryOrTerminal(
                    item = item,
                    errorMessage = "Unexpected delivery failure: ${e.message ?: e::class.java.simpleName}",
                    retryAfterMs = null,
                    current = current
                ) || anyFailed
            }
        }

        return anyFailed
    }

    /** Processes one row. Returns `true` when the row was rescheduled for another attempt. */
    private suspend fun processRow(item: OutboxEntity, current: Long): Boolean {
        val server = serverDao.getServerByConnectionIdDirect(item.connectionId)
            ?: serverDao.getServerByServerIdDirect(item.fromServer)

        if (server == null) {
            outboxDao.markTerminal(
                responseId = item.responseId,
                errorCode = SERVER_UNCONFIGURED,
                errorMessage = "Server profile for '${item.fromServer}' was removed or unconfigured",
                now = current
            )
            return false
        }

        if (!server.isEnabled) {
            // Local precondition, not a delivery attempt: stay retryable and manually recoverable,
            // polling slowly so a re-enabled server is picked up without a tight retry loop.
            outboxDao.scheduleRetry(
                responseId = item.responseId,
                attemptCount = item.attemptCount,
                nextAttemptAt = current + SERVER_DISABLED_RETRY_MS,
                errorMessage = "Server connection is disabled",
                now = current
            )
            return true
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
                return false
            }
            if (receipt != null) {
                outboxDao.recordReceipt(
                    responseId = item.responseId,
                    receiptResponseId = receipt.responseId,
                    receiptAcceptedAt = Instant.parse(receipt.acceptedAt).toEpochMilli(),
                    now = current
                )
                return false
            }
            // A 2xx without a receipt is not delivered: replaying the same UUID returns the receipt.
            return scheduleRetryOrTerminal(
                item = item,
                errorMessage = "Server accepted the response without returning a receipt",
                retryAfterMs = null,
                current = current
            )
        }

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
            return false
        }

        val classification = ErrorClassifier.classify(httpCode, deliveryException?.response)
        if (classification.isTerminal) {
            outboxDao.markTerminal(
                responseId = item.responseId,
                errorCode = classification.errorCode ?: "unknown",
                errorMessage = exception?.message ?: "Transmission rejected",
                now = current
            )
            return false
        }

        return scheduleRetryOrTerminal(
            item = item,
            errorMessage = exception?.message ?: "Transmission failed",
            retryAfterMs = classification.retryAfterMs,
            current = current
        )
    }

    /**
     * Persists the next attempt with exponential backoff, or marks the row terminal once the
     * persisted attempt count reaches [MAX_DELIVERY_ATTEMPTS].
     *
     * @return `true` when the row was rescheduled for another attempt.
     */
    private suspend fun scheduleRetryOrTerminal(
        item: OutboxEntity,
        errorMessage: String?,
        retryAfterMs: Long?,
        current: Long
    ): Boolean {
        val attemptCount = item.attemptCount + 1
        if (attemptCount >= MAX_DELIVERY_ATTEMPTS) {
            outboxDao.markTerminal(
                responseId = item.responseId,
                errorCode = RETRY_EXHAUSTED,
                errorMessage = errorMessage ?: "Retry limit reached",
                now = current
            )
            return false
        }

        outboxDao.scheduleRetry(
            responseId = item.responseId,
            attemptCount = attemptCount,
            nextAttemptAt = current + ErrorClassifier.calculateBackoff(attemptCount, retryAfterMs),
            errorMessage = errorMessage,
            now = current
        )
        return true
    }

    companion object {
        /** Bounded delivery attempts before a retryable failure becomes terminal; mirrors the server's eight-attempt worker bound. */
        const val MAX_DELIVERY_ATTEMPTS = 8

        /** Stable terminal code for a row whose server profile was removed or never configured. */
        const val SERVER_UNCONFIGURED = "server_unconfigured"

        /** Stable terminal code for a row that exhausted [MAX_DELIVERY_ATTEMPTS]. */
        const val RETRY_EXHAUSTED = "retry_exhausted"

        /** Slow poll while a server is disabled locally; keeps the row retryable and manually recoverable. */
        const val SERVER_DISABLED_RETRY_MS = 60_000L
    }
}
