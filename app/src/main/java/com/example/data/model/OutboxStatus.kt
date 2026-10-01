package com.example.data.model

enum class OutboxStatus {
    QUEUED,
    SENDING,
    AUTH_REQUIRED,
    FAILED,
    SENT,
    SIMULATED
}
