package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NotifyrDatabase
import com.example.data.model.ActionItem
import com.example.data.model.ActionKind
import com.example.data.model.NotificationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OutboxRepositoryValidationTest {

    private lateinit var db: NotifyrDatabase
    private lateinit var repository: OutboxRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyrDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = OutboxRepository(
            ApplicationProvider.getApplicationContext(),
            db.outboxDao(),
            db.notificationDao(),
            db.serverDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun notification(id: Long, actions: List<ActionItem>) = NotificationEntity(
        id = id,
        createdAt = System.currentTimeMillis(),
        fromServer = "srv-prod-us",
        fromApp = "auth-gate",
        title = "Approval",
        body = "Approve?",
        actions = actions
    )

    @Test
    fun `queueResponse rejects action id absent from notification actions`() = runBlocking {
        val notif = notification(1001L, listOf(ActionItem("known", "Approve", ActionKind.BUTTON)))
        db.notificationDao().insertNotification(notif)

        val result = repository.queueResponse(notif, ActionItem("unknown", "Reject", ActionKind.BUTTON))

        assertNull(result)
        assertEquals(0, db.outboxDao().countResponsesForNotification("srv-prod-us", 1001L))
        assertFalse(db.notificationDao().getNotificationDirect("srv-prod-us", 1001L)!!.isResponded)
    }

    @Test
    fun `queueResponse accepts action id present in notification actions`() = runBlocking {
        val action = ActionItem("known", "Approve", ActionKind.BUTTON)
        val notif = notification(1002L, listOf(action))
        db.notificationDao().insertNotification(notif)

        val responseId = repository.queueResponse(notif, action)

        assertNotNull(responseId)
        assertEquals("known", db.outboxDao().getResponseById(responseId!!)?.actionId)
    }

    @Test
    fun `queueResponse rejects blank TEXT_REPLY text`() = runBlocking {
        val action = ActionItem("reply", "Reply", ActionKind.TEXT_REPLY)
        val notif = notification(1003L, listOf(action))
        db.notificationDao().insertNotification(notif)

        assertNull(repository.queueResponse(notif, action, ""))
        assertNull(repository.queueResponse(notif, action, "   "))
        assertNull(repository.queueResponse(notif, action, null))
    }

    @Test
    fun `queueResponse trims TEXT_REPLY text once before persisting`() = runBlocking {
        val action = ActionItem("reply", "Reply", ActionKind.TEXT_REPLY)
        val notif = notification(1004L, listOf(action))
        db.notificationDao().insertNotification(notif)

        val responseId = repository.queueResponse(notif, action, "  Acknowledged  ")

        assertNotNull(responseId)
        assertEquals("Acknowledged", db.outboxDao().getResponseById(responseId!!)?.replyText)
    }

    @Test
    fun `queueResponse enforces 4096 UTF-8 byte limit`() = runBlocking {
        val action = ActionItem("reply", "Reply", ActionKind.TEXT_REPLY)
        val ids = listOf(1005L, 1006L, 1007L, 1008L)
        ids.forEach { db.notificationDao().insertNotification(notification(it, listOf(action))) }

        assertNotNull(repository.queueResponse(notification(1005L, listOf(action)), action, "a".repeat(4096)))
        assertNull(repository.queueResponse(notification(1006L, listOf(action)), action, "a".repeat(4097)))
        assertNotNull(repository.queueResponse(notification(1007L, listOf(action)), action, "한".repeat(1365)))
        assertNull(repository.queueResponse(notification(1008L, listOf(action)), action, "한".repeat(1366)))
    }
}
