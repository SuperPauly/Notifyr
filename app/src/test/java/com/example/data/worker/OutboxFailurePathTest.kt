package com.example.data.worker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NotifyrDatabase
import com.example.data.local.OutboxDao
import com.example.data.local.ServerDao
import com.example.data.model.ActionKind
import com.example.data.model.OutboxEntity
import com.example.data.model.OutboxStatus
import com.example.data.model.ServerEntity
import com.example.data.model.canManuallyRetry
import com.example.data.model.isRetryPending
import com.example.data.remote.model.ResponseReceiptDto
import com.example.data.transport.NotificationResponseTransport
import com.example.data.transport.ResponseDeliveryException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class OutboxFailurePathTest {

    private lateinit var db: NotifyrDatabase
    private lateinit var outboxDao: OutboxDao
    private lateinit var serverDao: ServerDao
    private lateinit var engine: OutboxSyncEngine
    private var clock = 1_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyrDatabase::class.java
        ).allowMainThreadQueries().build()
        outboxDao = db.outboxDao()
        serverDao = db.serverDao()
        clock = 1_000_000L
    }

    @After
    fun tearDown() {
        db.close()
    }

    private class RecordingTransport(
        private val responder: (ServerEntity, OutboxEntity) -> Result<ResponseReceiptDto?>
    ) : NotificationResponseTransport {
        val calls = mutableListOf<Pair<ServerEntity, OutboxEntity>>()

        override suspend fun deliverResponse(
            server: ServerEntity,
            response: OutboxEntity
        ): Result<ResponseReceiptDto?> {
            calls += server to response
            return responder(server, response)
        }
    }

    private fun buildEngine(
        responder: (ServerEntity, OutboxEntity) -> Result<ResponseReceiptDto?>
    ): RecordingTransport {
        val transport = RecordingTransport(responder)
        engine = OutboxSyncEngine(outboxDao, serverDao, transport) { clock }
        return transport
    }

    private fun server(enabled: Boolean = true) = ServerEntity(
        connectionId = "conn-1",
        serverId = "srv-1",
        displayName = "Prod",
        baseUrl = "https://srv.example",
        isEnabled = enabled
    )

    private fun outbox(
        responseId: String = UUID.randomUUID().toString(),
        status: OutboxStatus = OutboxStatus.QUEUED,
        attemptCount: Int = 0,
        receiptResponseId: String? = null,
        receiptAcceptedAt: Long? = null
    ) = OutboxEntity(
        responseId = responseId,
        notificationId = 1L,
        fromServer = "srv-1",
        actionId = "act-1",
        actionLabel = "Approve",
        actionKind = ActionKind.BUTTON,
        connectionId = "conn-1",
        notificationTitle = "Title",
        fromApp = "app",
        status = status,
        attemptCount = attemptCount,
        receiptResponseId = receiptResponseId,
        receiptAcceptedAt = receiptAcceptedAt
    )

    private fun httpError(code: Int): Response<Any> {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://srv.example/v1/responses").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("error")
            .build()
        return Response.error("{}".toResponseBody("application/json".toMediaType()), raw)
    }

    @Test
    fun `missing server profile becomes terminal server_unconfigured and is never retried`() = runBlocking {
        // Given a queued response whose server profile no longer exists
        val row = outbox()
        outboxDao.insertResponse(row)
        val transport = buildEngine { _, _ -> error("transport must not be called") }

        // When the engine runs
        val anyFailed = engine.runOnce()

        // Then the row is terminal with a stable code and requires no further attempt
        assertFalse(anyFailed)
        val saved = outboxDao.getResponseById(row.responseId)!!
        assertEquals(OutboxStatus.FAILED, saved.status)
        assertEquals("server_unconfigured", saved.terminalErrorCode)
        assertNull(saved.nextAttemptAt)
        assertEquals(0, transport.calls.size)

        engine.runOnce()
        assertEquals(0, transport.calls.size)
    }

    @Test
    fun `disabled server stays retryable and manually recoverable without a tight retry`() = runBlocking {
        // Given an enabled-then-disabled server
        serverDao.insertServer(server(enabled = false))
        val row = outbox()
        outboxDao.insertResponse(row)
        val transport = buildEngine { _, _ -> error("transport must not be called") }

        // When the engine runs
        val anyFailed = engine.runOnce()

        // Then the row is a retryable FAILED, not a dead-end AUTH_REQUIRED, and no delivery was attempted
        assertTrue(anyFailed)
        assertEquals(0, transport.calls.size)
        val saved = outboxDao.getResponseById(row.responseId)!!
        assertEquals(OutboxStatus.FAILED, saved.status)
        assertNull(saved.terminalErrorCode)
        assertEquals(clock + OutboxSyncEngine.SERVER_DISABLED_RETRY_MS, saved.nextAttemptAt)
        assertTrue(saved.isRetryPending(clock))
        assertFalse(saved.canManuallyRetry(clock))
        assertTrue(saved.canManuallyRetry(clock + OutboxSyncEngine.SERVER_DISABLED_RETRY_MS + 1))
    }

    @Test
    fun `non-simulated success without a receipt is not marked SENT and is replayed`() = runBlocking {
        // Given a transport that acknowledges without returning a receipt
        serverDao.insertServer(server())
        val row = outbox()
        outboxDao.insertResponse(row)
        val transport = buildEngine { _, _ -> Result.success(null) }

        // When the engine runs
        val anyFailed = engine.runOnce()

        // Then the row is not SENT and is retried with the same response_id
        assertTrue(anyFailed)
        assertEquals(1, transport.calls.size)
        val saved = outboxDao.getResponseById(row.responseId)!!
        assertEquals(OutboxStatus.FAILED, saved.status)
        assertNull(saved.terminalErrorCode)
        assertNotNull(saved.nextAttemptAt)
        assertNull(saved.receiptResponseId)

        clock = saved.nextAttemptAt!! + 1
        engine.runOnce()
        assertEquals(2, transport.calls.size)
        assertEquals(row.responseId, transport.calls.last().second.responseId)
    }

    @Test
    fun `receipt parse exception resets the row from SENDING to a retryable FAILED`() = runBlocking {
        // Given a malformed accepted_at timestamp in an otherwise successful receipt
        serverDao.insertServer(server())
        val row = outbox()
        outboxDao.insertResponse(row)
        buildEngine { _, _ -> Result.success(ResponseReceiptDto("rcpt-1", "not-a-timestamp")) }

        // When the engine runs (the parse must not escape and strand the row in SENDING)
        val anyFailed = engine.runOnce()

        // Then the row is a retryable FAILED
        assertTrue(anyFailed)
        val saved = outboxDao.getResponseById(row.responseId)!!
        assertEquals(OutboxStatus.FAILED, saved.status)
        assertNull(saved.terminalErrorCode)
        assertNotNull(saved.nextAttemptAt)
    }

    @Test
    fun `retry cap marks the row terminal with retry_exhausted after the bound`() = runBlocking {
        // Given a row one attempt short of the persisted bound
        serverDao.insertServer(server())
        val row = outbox(attemptCount = OutboxSyncEngine.MAX_DELIVERY_ATTEMPTS - 1)
        outboxDao.insertResponse(row)
        val transport = buildEngine { _, _ -> Result.failure(ResponseDeliveryException(500, httpError(500))) }

        // When the next delivery fails retryably
        val anyFailed = engine.runOnce()

        // Then the row is terminal and no further attempt is made
        assertFalse(anyFailed)
        assertEquals(1, transport.calls.size)
        val saved = outboxDao.getResponseById(row.responseId)!!
        assertEquals(OutboxStatus.FAILED, saved.status)
        assertEquals("retry_exhausted", saved.terminalErrorCode)
        assertNull(saved.nextAttemptAt)

        engine.runOnce()
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun `retryable failure below the bound still schedules the next attempt`() = runBlocking {
        // Given a row well below the persisted bound
        serverDao.insertServer(server())
        val row = outbox(attemptCount = OutboxSyncEngine.MAX_DELIVERY_ATTEMPTS - 2)
        outboxDao.insertResponse(row)
        buildEngine { _, _ -> Result.failure(ResponseDeliveryException(500, httpError(500))) }

        // When the next delivery fails retryably
        val anyFailed = engine.runOnce()

        // Then the attempt count advances and a next attempt is scheduled
        assertTrue(anyFailed)
        val saved = outboxDao.getResponseById(row.responseId)!!
        assertEquals(OutboxSyncEngine.MAX_DELIVERY_ATTEMPTS - 1, saved.attemptCount)
        assertNull(saved.terminalErrorCode)
        assertNotNull(saved.nextAttemptAt)
    }
}
