package com.example.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.NotifyrApplication
import com.example.data.local.NotifyrDatabase
import com.example.data.model.ActionKind
import com.example.data.model.OutboxEntity
import com.example.data.model.OutboxStatus
import com.example.data.model.ServerEntity
import com.example.data.remote.model.ResponseReceiptDto
import com.example.data.transport.NotificationResponseTransport
import com.example.data.transport.ResponseDeliveryException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, manifest = Config.NONE)
class OutboxSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var database: NotifyrDatabase
    private lateinit var transport: FakeResponseTransport

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val app = context as TestApplication
        database = app.database
        transport = FakeResponseTransport()
        app.responseTransport = transport
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `receipt persistence before SENT status`() = runBlocking {
        val server = createTestServer()
        val outbox = createTestOutbox(server)
        database.serverDao().insertServer(server)
        database.outboxDao().insertResponse(outbox)

        val receipt = ResponseReceiptDto(
            responseId = outbox.responseId,
            acceptedAt = "2026-10-12T20:00:00Z"
        )
        transport.setResult(Result.success(receipt))

        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val persisted = database.outboxDao().getResponseById(outbox.responseId)
        assertNotNull(persisted)
        assertEquals(OutboxStatus.SENT, persisted!!.status)
        assertEquals(receipt.responseId, persisted.receiptResponseId)
        assertNotNull(persisted.receiptAcceptedAt)
    }

    @Test
    fun `terminal error classification`() = runBlocking {
        val server = createTestServer()
        val outbox = createTestOutbox(server)
        database.serverDao().insertServer(server)
        database.outboxDao().insertResponse(outbox)

        transport.setResult(Result.failure(ResponseDeliveryException(409, null)))

        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val persisted = database.outboxDao().getResponseById(outbox.responseId)
        assertEquals(OutboxStatus.FAILED, persisted!!.status)
        assertNotNull(persisted.terminalErrorCode)
    }

    @Test
    fun `retryable error schedules retry`() = runBlocking {
        val server = createTestServer()
        val outbox = createTestOutbox(server)
        database.serverDao().insertServer(server)
        database.outboxDao().insertResponse(outbox)

        transport.setResult(Result.failure(ResponseDeliveryException(500, null)))

        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)

        val persisted = database.outboxDao().getResponseById(outbox.responseId)
        assertEquals(OutboxStatus.FAILED, persisted!!.status)
        assertNotNull(persisted.nextAttemptAt)
        assertNull(persisted.terminalErrorCode)
    }

    private fun createTestServer() = ServerEntity(
        serverId = "test-server",
        connectionId = UUID.randomUUID().toString(),
        displayName = "Test Server",
        baseUrl = "https://test.example.com",
        encryptedAuthToken = ByteArray(32),
        subscriptionStatus = "active",
        isEnabled = true
    )

    private fun createTestOutbox(server: ServerEntity) = OutboxEntity(
        responseId = UUID.randomUUID().toString(),
        connectionId = server.connectionId,
        fromServer = server.serverId,
        notificationId = 1L,
        actionId = "action-1",
        actionKind = ActionKind.BUTTON,
        replyText = null,
        status = OutboxStatus.QUEUED,
        isSimulated = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private class FakeResponseTransport : NotificationResponseTransport {
        private var result: Result<ResponseReceiptDto?> = Result.success(null)

        fun setResult(result: Result<ResponseReceiptDto?>) {
            this.result = result
        }

        override suspend fun deliverResponse(
            server: ServerEntity,
            response: OutboxEntity
        ): Result<ResponseReceiptDto?> = result
    }
}

class TestApplication : NotifyrApplication() {
    override val database: NotifyrDatabase by lazy {
        NotifyrDatabase.buildInMemory(this)
    }
    
    override val responseTransport: NotificationResponseTransport
        get() = super.responseTransport
}
