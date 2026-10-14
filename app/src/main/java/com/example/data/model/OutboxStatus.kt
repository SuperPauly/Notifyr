package com.example.data.model

enum class OutboxStatus {
    QUEUED,
    SENDING,
    AUTH_REQUIRED,
    FAILED,
    SENT,
    SIMULATED
}

val OutboxStatus.displayLabel: String
    get() = when (this) {
        OutboxStatus.QUEUED -> "Queued"
        OutboxStatus.SENDING -> "Sending"
        OutboxStatus.AUTH_REQUIRED -> "Auth required"
        OutboxStatus.FAILED -> "Failed"
        OutboxStatus.SENT -> "Sent"
        OutboxStatus.SIMULATED -> "Simulated"
    }

val OutboxEntity.isAccepted: Boolean
    get() = receiptResponseId != null && receiptAcceptedAt != null

val OutboxEntity.deleteRequiresConfirmation: Boolean
    get() = status == OutboxStatus.QUEUED && !isAccepted

fun OutboxEntity.canManuallyRetry(now: Long): Boolean {
    if (status != OutboxStatus.FAILED || terminalErrorCode != null) return false
    return nextAttemptAt == null || nextAttemptAt <= now
}

fun OutboxEntity.isRetryPending(now: Long): Boolean {
    return status == OutboxStatus.FAILED &&
        terminalErrorCode == null &&
        nextAttemptAt != null &&
        nextAttemptAt > now
}

fun OutboxEntity.displayStatus(now: Long = System.currentTimeMillis()): String = if (isRetryPending(now)) {
        "Retrying"
    } else {
        status.displayLabel
    }
