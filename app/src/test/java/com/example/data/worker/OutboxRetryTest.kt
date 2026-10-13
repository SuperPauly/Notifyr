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
import com.example.data.remote.model.ResponseReceiptDto
import com.example.data.response.ResponseSerializer
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
class OutboxRetryTest {

    private lateinit var db: NotifyrDatabase
    private lateinit var outboxDao: OutboxDao
    private lateinit var serverDao: ServerDao
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

    private lateinit var engine: OutboxSyncEngine

    private fun server() = ServerEntity(
        connectionId = "conn-1",
        serverId = "srv-1",
        displayName = "Prod",
        baseUrl = "https://srv.example"
    )

    private fun outbox(
        responseId: String = UUID.randomUUID().toString(),
        status: OutboxStatus = OutboxStatus.QUEUED,
        actionKind: ActionKind = ActionKind.BUTTON,
        replyText: String? = null,
        receiptResponseId: String? = null,
        receiptAcceptedAt: Long? = null
    ) = OutboxEntity(
        responseId = responseId,
        notificationId = 1L,
        fromServer = "srv-1",
        actionId = "act-1",
        actionLabel = "Approve",
        actionKind = actionKind,
        replyText = replyText,
        connectionId = "conn-1",
        notificationTitle = "Title",
        fromApp = "app",
        status = status,
        receiptResponseId = receiptResponseId,
        receiptAcceptedAt = receiptAcceptedAt
    )

    private fun httpError(code: Int, retryAfterSeconds: Long? = null): Response<Any> {
        val rawBuilder = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://srv.example/v1/responses").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("error")
        if (retryAfterSeconds != null) {
            rawBuilder.header("Retry-After", retryAfterSeconds.toString())
        }
        val body = "{}".toResponseBody("application/json".toMediaType())
        return Response.error(body, rawBuilder.build())
    }

    @Test
    fun `retryable failure persists attempt_count and next_attempt_at`() = runBlocking {
        serverDao.insertServer(server())
        val row = outbox()
        outboxDao.insertResponse(row)
        buildEngine { _, _ -> Result.failure(ResponseDeliveryException(500, httpError(500))) }

        val anyFailed = engine.runOnce()

        assertTrue(anyFailed)
        val saved = outboxDao.getResponseById(row.responseId)!!
        assertEquals(OutboxStatus.FAILED, saved.status)
        assertEquals(1, saved.attemptCount)
        assertNull(saved.terminalErrorCode)
        assertNotNull(saved.nextAttemptAt)
        assertTrue(saved.nextAttemptAt!! - clock in 1000..1300)
    }

    @Test
    fun `Retry-After header controls the next attempt delay`() = runBlocking {
        serverDao.insertServer(server())
        val row = outbox()
        outboxDao.insertResponse(row)
        buildEngine { _, _ ->
            Result.failure(ResponseDeliveryException(429, httpError(429, retryAfterSeconds = 5)))
        }

        engine.runOnce()

        val saved = outboxDao.getResponseById(row.responseId)!!
        assertEquals(5000L, saved.nextAttemptAt!! - clock)
    }

    @Test
    fun `retry waits for the persisted window and reuses the persisted attempt_count`() = runBlocking {
        serverDao.insertServer(server())
        val row = outbox()
        outboxDao.insertResponse(row)
        val transport = buildEngine { _, _ ->
            Result.failure(ResponseDeliveryException(500, httpError(500)))
        }

        engine.runOnce()
        val firstAttempt = outboxDao.getResponseById(row.responseId)!!
        assertEquals(1, firstAttempt.attemptCount)

        clock = firstAttempt.nextAttemptAt!! - 1
        val skipped = engine.runOnce()
        assertFalse(skipped)
        assertEquals(1, transport.calls.size)
        assertEquals(1, outboxDao.getResponseById(row.responseId)!!.attemptCount)

        clock = firstAttempt.nextAttemptAt!! + 1
        engine.runOnce()
        assertEquals(2, transport.calls.size)
        assertEquals(2, outboxDao.getResponseById(row.responseId)!!.attemptCount)
    }

    @Test
    fun `terminal failure records a terminal code and stops future attempts`() = runBlocking {
        serverDao.insertServer(server())
        val row = outbox()
        outboxDao.insertResponse(row)
        val transport = buildEngine { _, _ ->
            Result.failure(ResponseDeliveryException(409, httpError(409)))
        }

        val anyFailed = engine.runOnce()

        assertFalse(anyFailed)
        val saved = outboxDao.getResponseById(row.responseId)!!
        assertEquals(OutboxStatus.FAILED, saved.status)
        assertEquals("already_responded", saved.terminalErrorCode)
        assertNull(saved.nextAttemptAt)

        engine.runOnce()
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun `410 with an existing receipt is treated as success`() = runBlocking {
        serverDao.insertServer(server())
        val row = outbox(receiptResponseId = "rcpt-old", receiptAcceptedAt = 42L)
        outboxDao.insertResponse(row)
        buildEngine { _, _ -> Result.failure(ResponseDeliveryException(410, httpError(410))) }

        val anyFailed = engine.runOnce()

        assertFalse(anyFailed)
        val saved = outboxDao.getResponseById(row.responseId)!!
        assertEquals(OutboxStatus.SENT, saved.status)
        assertEquals("rcpt-old", saved.receiptResponseId)
        assertEquals(42L, saved.receiptAcceptedAt)
        assertNull(saved.terminalErrorCode)
    }

    @Test
    fun `retries reuse the persisted response_id and payload byte-for-byte`() = runBlocking {
        serverDao.insertServer(server())
        val row = outbox(actionKind = ActionKind.TEXT_REPLY, replyText = "Approve the deploy")
        outboxDao.insertResponse(row)
        val transport = buildEngine { _, _ ->
            Result.failure(ResponseDeliveryException(500, httpError(500)))
        }

        engine.runOnce()
        val firstCall = transport.calls.last().second
        clock = outboxDao.getResponseById(row.responseId)!!.nextAttemptAt!! + 1
        engine.runOnce()
        val secondCall = transport.calls.last().second

        assertEquals(firstCall.responseId, secondCall.responseId)
        assertEquals(
            ResponseSerializer.serializeResponse(firstCall.actionId, firstCall.actionKind, firstCall.replyText),
            ResponseSerializer.serializeResponse(secondCall.actionId, secondCall.actionKind, secondCall.replyText)
        )
    }
}
