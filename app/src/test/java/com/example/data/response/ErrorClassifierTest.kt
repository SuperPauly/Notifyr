package com.example.data.response

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ErrorClassifierTest {

    private val json = "application/json".toMediaType()

    private fun errorResponse(code: Int, body: String = "", retryAfter: String? = null): Response<Any> {
        val headers = Headers.Builder()
            .apply { if (retryAfter != null) add("Retry-After", retryAfter) }
            .build()
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("error")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .headers(headers)
            .build()
        return Response.error(body.toResponseBody(json), raw)
    }

    // --- pure classification: success and terminal codes ---

    @Test
    fun `2xx success is neither retryable nor terminal`() {
        val result = ErrorClassifier.classify(200, null, null)

        assertFalse(result.isRetryable)
        assertFalse(result.isTerminal)
        assertNull(result.errorCode)
        assertNull(result.retryAfterMs)
    }

    @Test
    fun `401 unauthorized is terminal`() {
        val result = ErrorClassifier.classify(401, "unauthorized", null)

        assertFalse(result.isRetryable)
        assertTrue(result.isTerminal)
        assertEquals("unauthorized", result.errorCode)
        assertNull(result.retryAfterMs)
    }

    @Test
    fun `403 scope_required is terminal`() {
        val result = ErrorClassifier.classify(403, "scope_required", null)

        assertFalse(result.isRetryable)
        assertTrue(result.isTerminal)
        assertEquals("scope_required", result.errorCode)
    }

    @Test
    fun `409 already_responded is terminal`() {
        val result = ErrorClassifier.classify(409, "already_responded", null)

        assertFalse(result.isRetryable)
        assertTrue(result.isTerminal)
        assertEquals("already_responded", result.errorCode)
    }

    @Test
    fun `409 idempotency_conflict is terminal`() {
        val result = ErrorClassifier.classify(409, "idempotency_conflict", null)

        assertFalse(result.isRetryable)
        assertTrue(result.isTerminal)
        assertEquals("idempotency_conflict", result.errorCode)
    }

    @Test
    fun `409 without an error body defaults to already_responded`() {
        val result = ErrorClassifier.classify(409, null, null)

        assertTrue(result.isTerminal)
        assertEquals("already_responded", result.errorCode)
    }

    @Test
    fun `410 notification_expired is terminal`() {
        val result = ErrorClassifier.classify(410, "notification_expired", null)

        assertFalse(result.isRetryable)
        assertTrue(result.isTerminal)
        assertEquals("notification_expired", result.errorCode)
    }

    @Test
    fun `422 invalid_request is terminal`() {
        val result = ErrorClassifier.classify(422, "invalid_request", null)

        assertFalse(result.isRetryable)
        assertTrue(result.isTerminal)
        assertEquals("invalid_request", result.errorCode)
    }

    @Test
    fun `404 notification_not_found is terminal`() {
        val result = ErrorClassifier.classify(404, "notification_not_found", null)

        assertFalse(result.isRetryable)
        assertTrue(result.isTerminal)
        assertEquals("notification_not_found", result.errorCode)
    }

    @Test
    fun `unmapped status is terminal`() {
        val result = ErrorClassifier.classify(418, "teapot", null)

        assertFalse(result.isRetryable)
        assertTrue(result.isTerminal)
        assertEquals("teapot", result.errorCode)
    }

    // --- pure classification: retryable codes ---

    @Test
    fun `408 timeout is retryable`() {
        val result = ErrorClassifier.classify(408, null, null)

        assertTrue(result.isRetryable)
        assertFalse(result.isTerminal)
        assertEquals("timeout", result.errorCode)
    }

    @Test
    fun `425 too early is retryable`() {
        val result = ErrorClassifier.classify(425, null, null)

        assertTrue(result.isRetryable)
        assertFalse(result.isTerminal)
        assertEquals("timeout", result.errorCode)
    }

    @Test
    fun `429 rate_limited is retryable`() {
        val result = ErrorClassifier.classify(429, null, null)

        assertTrue(result.isRetryable)
        assertFalse(result.isTerminal)
        assertEquals("rate_limited", result.errorCode)
    }

    @Test
    fun `429 honors Retry-After 30 seconds`() {
        val result = ErrorClassifier.classify(429, null, "30")

        assertTrue(result.isRetryable)
        assertEquals(30_000L, result.retryAfterMs)
    }

    @Test
    fun `503 not_ready is retryable`() {
        val result = ErrorClassifier.classify(503, "not_ready", null)

        assertTrue(result.isRetryable)
        assertFalse(result.isTerminal)
        assertEquals("not_ready", result.errorCode)
    }

    @Test
    fun `503 without an error body defaults to server_error retryable`() {
        val result = ErrorClassifier.classify(503, null, null)

        assertTrue(result.isRetryable)
        assertFalse(result.isTerminal)
        assertEquals("server_error", result.errorCode)
    }

    @Test
    fun `5xx server_error is retryable`() {
        val result = ErrorClassifier.classify(500, null, null)

        assertTrue(result.isRetryable)
        assertFalse(result.isTerminal)
        assertEquals("server_error", result.errorCode)
    }

    @Test
    fun `network error (code 0) is retryable`() {
        val result = ErrorClassifier.classify(0, null, null)

        assertTrue(result.isRetryable)
        assertFalse(result.isTerminal)
        assertEquals("network_error", result.errorCode)
        assertNull(result.retryAfterMs)
    }

    // --- Retrofit Response overload extraction ---

    @Test
    fun `Response overload extracts idempotency_conflict from the error body`() {
        val result = ErrorClassifier.classify(409, errorResponse(409, """{"error":"idempotency_conflict"}"""))

        assertFalse(result.isRetryable)
        assertTrue(result.isTerminal)
        assertEquals("idempotency_conflict", result.errorCode)
    }

    @Test
    fun `Response overload extracts not_ready from a 503 body`() {
        val result = ErrorClassifier.classify(503, errorResponse(503, """{"error":"not_ready"}"""))

        assertTrue(result.isRetryable)
        assertEquals("not_ready", result.errorCode)
    }

    @Test
    fun `Response overload extracts Retry-After seconds`() {
        val result = ErrorClassifier.classify(
            429,
            errorResponse(429, """{"error":"rate_limited"}""", retryAfter = "30")
        )

        assertTrue(result.isRetryable)
        assertEquals(30_000L, result.retryAfterMs)
    }

    @Test
    fun `Response overload tolerates a non-JSON error body`() {
        val result = ErrorClassifier.classify(500, errorResponse(500, "gateway exploded"))

        assertTrue(result.isRetryable)
        assertEquals("server_error", result.errorCode)
    }

    // --- backoff ---

    @Test
    fun `calculateBackoff respects Retry-After`() {
        val backoff = ErrorClassifier.calculateBackoff(1, 30000L)

        assertEquals(30000L, backoff)
    }

    @Test
    fun `calculateBackoff exponential with jitter`() {
        val backoff = ErrorClassifier.calculateBackoff(3, null)

        assertTrue(backoff >= 4000L)
        assertTrue(backoff <= 5200L)
    }

    @Test
    fun `calculateBackoff caps at 24h`() {
        val backoff = ErrorClassifier.calculateBackoff(100, null)

        assertTrue(backoff <= 86400000L)
    }
}
