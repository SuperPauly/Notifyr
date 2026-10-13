package com.example.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActionKind
import com.example.data.model.OutboxEntity
import com.example.data.model.OutboxStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ReceiptPersistenceTest {

    private lateinit var db: NotifyrDatabase
    private lateinit var outboxDao: OutboxDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyrDatabase::class.java
        ).allowMainThreadQueries().build()
        outboxDao = db.outboxDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun outbox(
        responseId: String = UUID.randomUUID().toString(),
        status: OutboxStatus = OutboxStatus.QUEUED,
        receiptResponseId: String? = null,
        receiptAcceptedAt: Long? = null
    ) = OutboxEntity(
        responseId = responseId,
        notificationId = 1L,
        fromServer = "srv-1",
        actionId = "act-1",
        actionLabel = "Approve",
        actionKind = ActionKind.BUTTON,
        replyText = null,
        connectionId = "conn-1",
        notificationTitle = "Title",
        fromApp = "app",
        status = status,
        receiptResponseId = receiptResponseId,
        receiptAcceptedAt = receiptAcceptedAt
    )

    @Test
    fun `recordReceipt stores the receipt and flips status to SENT together`() = runBlocking {
        val row = outbox()
        outboxDao.insertResponse(row)

        outboxDao.recordReceipt(
            responseId = row.responseId,
            receiptResponseId = "rcpt-1",
            receiptAcceptedAt = 555L,
            now = 999L
        )

        val saved = outboxDao.getResponseById(row.responseId)
        assertNotNull(saved)
        assertEquals(OutboxStatus.SENT, saved!!.status)
        assertEquals("rcpt-1", saved.receiptResponseId)
        assertEquals(555L, saved.receiptAcceptedAt)
        assertNull(saved.errorMessage)
    }

    @Test
    fun `clearSent keeps a SENT row that has no receipt`() = runBlocking {
        val row = outbox(status = OutboxStatus.SENT)
        outboxDao.insertResponse(row)

        outboxDao.clearSent()

        assertNotNull(outboxDao.getResponseById(row.responseId))
    }

    @Test
    fun `clearSent removes a SENT row once its receipt is stored`() = runBlocking {
        val row = outbox(
            status = OutboxStatus.SENT,
            receiptResponseId = "rcpt-2",
            receiptAcceptedAt = 42L
        )
        outboxDao.insertResponse(row)

        outboxDao.clearSent()

        assertNull(outboxDao.getResponseById(row.responseId))
    }

    @Test
    fun `clearSent removes simulated rows and keeps queued rows`() = runBlocking {
        val simulated = outbox(status = OutboxStatus.SIMULATED)
        val queued = outbox(status = OutboxStatus.QUEUED)
        outboxDao.insertResponse(simulated)
        outboxDao.insertResponse(queued)

        outboxDao.clearSent()

        assertNull(outboxDao.getResponseById(simulated.responseId))
        assertNotNull(outboxDao.getResponseById(queued.responseId))
    }
}
