package com.example.data.response

import retrofit2.Response

object ErrorClassifier {

    data class Classification(
        val isRetryable: Boolean,
        val isTerminal: Boolean,
        val errorCode: String?,
        val retryAfterMs: Long?
    )

    fun classify(httpCode: Int, response: Response<*>?): Classification {
        val retryAfter = response?.headers()?.get("Retry-After")?.toLongOrNull()?.let { it * 1000 }

        return when (httpCode) {
            in 200..299 -> Classification(
                isRetryable = false,
                isTerminal = false,
                errorCode = null,
                retryAfterMs = null
            )
            401, 403 -> Classification(
                isRetryable = false,
                isTerminal = true,
                errorCode = "unauthorized",
                retryAfterMs = null
            )
            409 -> Classification(
                isRetryable = false,
                isTerminal = true,
                errorCode = "already_responded",
                retryAfterMs = null
            )
            410 -> Classification(
                isRetryable = false,
                isTerminal = true,
                errorCode = "notification_expired",
                retryAfterMs = null
            )
            422 -> Classification(
                isRetryable = false,
                isTerminal = true,
                errorCode = "invalid_request",
                retryAfterMs = null
            )
            408, 425 -> Classification(
                isRetryable = true,
                isTerminal = false,
                errorCode = "timeout",
                retryAfterMs = retryAfter
            )
            429 -> Classification(
                isRetryable = true,
                isTerminal = false,
                errorCode = "rate_limited",
                retryAfterMs = retryAfter
            )
            in 500..599 -> Classification(
                isRetryable = true,
                isTerminal = false,
                errorCode = "server_error",
                retryAfterMs = retryAfter
            )
            else -> Classification(
                isRetryable = false,
                isTerminal = true,
                errorCode = "unknown_error",
                retryAfterMs = null
            )
        }
    }

    fun calculateBackoff(attemptCount: Int, retryAfterMs: Long?): Long {
        if (retryAfterMs != null) return retryAfterMs
        
        val baseDelayMs = 1000L
        val maxDelayMs = 24 * 60 * 60 * 1000L
        val exponentialDelay = baseDelayMs * (1 shl (attemptCount - 1).coerceIn(0, 10))
        val jitter = (Math.random() * 0.3 * exponentialDelay).toLong()
        
        return (exponentialDelay + jitter).coerceIn(baseDelayMs, maxDelayMs)
    }
}
