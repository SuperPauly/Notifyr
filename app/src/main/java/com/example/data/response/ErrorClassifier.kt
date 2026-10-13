package com.example.data.response

import retrofit2.Response

/**
 * Per API_CONTRACT.md "Stable error codes", every error body is `{"error":"<stable_code>"}`;
 * the body code is authoritative when present, else the status default applies.
 */
object ErrorClassifier {

    data class Classification(
        val isRetryable: Boolean,
        val isTerminal: Boolean,
        val errorCode: String?,
        val retryAfterMs: Long?
    )

    private val ERROR_CODE_REGEX = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"")

    /**
     * Pure classification.
     *
     * @param errorCode the stable code from the `{"error":"<code>"}` body, or `null` when absent.
     * @param retryAfterHeader the raw `Retry-After` header value, or `null`.
     */
    fun classify(httpCode: Int, errorCode: String?, retryAfterHeader: String?): Classification {
        val retryAfterMs = parseRetryAfterMs(retryAfterHeader)

        return when (httpCode) {
            in 200..299 -> Classification(false, false, null, null)
            401 -> terminal(errorCode, "unauthorized")
            403 -> terminal(errorCode, "unauthorized")
            409 -> terminal(errorCode, "already_responded")
            410 -> terminal(errorCode, "notification_expired")
            422 -> terminal(errorCode, "invalid_request")
            408, 425 -> retryable("timeout", retryAfterMs)
            429 -> retryable("rate_limited", retryAfterMs)
            // 503 not_ready (and other 503 codes) are retryable, distinct from generic 5xx.
            503 -> retryable(errorCode ?: "server_error", retryAfterMs)
            in 500..599 -> retryable(errorCode ?: "server_error", retryAfterMs)
            0 -> retryable("network_error", null)
            else -> terminal(errorCode, "unknown_error")
        }
    }

    /** Extracts the stable error code and `Retry-After` header from [response], then classifies. */
    fun classify(httpCode: Int, response: Response<*>?): Classification =
        classify(
            httpCode = httpCode,
            errorCode = extractErrorCode(response),
            retryAfterHeader = response?.headers()?.get("Retry-After")
        )

    fun calculateBackoff(attemptCount: Int, retryAfterMs: Long?): Long {
        if (retryAfterMs != null) return retryAfterMs

        val baseDelayMs = 1000L
        val maxDelayMs = 24 * 60 * 60 * 1000L
        val exponentialDelay = baseDelayMs * (1 shl (attemptCount - 1).coerceIn(0, 10))
        val jitter = (Math.random() * 0.3 * exponentialDelay).toLong()

        return (exponentialDelay + jitter).coerceIn(baseDelayMs, maxDelayMs)
    }

    private fun terminal(code: String?, fallback: String): Classification =
        Classification(isRetryable = false, isTerminal = true, errorCode = code ?: fallback, retryAfterMs = null)

    private fun retryable(code: String, retryAfterMs: Long?): Classification =
        Classification(isRetryable = true, isTerminal = false, errorCode = code, retryAfterMs = retryAfterMs)

    private fun parseRetryAfterMs(header: String?): Long? {
        val seconds = header?.trim()?.toLongOrNull() ?: return null
        return if (seconds >= 0) seconds * 1000 else null
    }

    private fun extractErrorCode(response: Response<*>?): String? =
        runCatching { response?.errorBody()?.string() }
            .getOrNull()
            ?.let { ERROR_CODE_REGEX.find(it) }
            ?.groupValues
            ?.getOrNull(1)
}
