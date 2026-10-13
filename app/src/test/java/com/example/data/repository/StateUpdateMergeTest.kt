package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NotificationDao
import com.example.data.local.NotifyrDatabase
import com.example.data.local.ServerDao
import com.example.data.model.ActionItem
import com.example.data.model.ActionKind
import com.example.data.model.NotificationEntity
import com.example.data.model.ServerEntity
import com.example.data.remote.model.NotificationDto
import com.example.data.remote.model.NotificationStateDto
import com.example.data.transport.InboxPageResult
import com.example.data.transport.InboxTransport
import com.example.data.transport.NotificationFetchResult
import com.example.data.unifiedpush.DecodeResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Merge semantics for `state_update` and inline states: local read/respond state survives,
 * actions are dropped once answered, and revision ordering gates every write.
 */
@RunWith(RobolectricTestRunner::class)
class StateUpdateMergeTest {

    private lateinit var db: NotifyrDatabase
    private lateinit var notificationDao: NotificationDao
    private lateinit var serverDao: ServerDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyrDatabase::class.java
        ).allowMainThreadQueries().build()
        notificationDao = db.notificationDao()
        serverDao = db.serverDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private class NoopTransport : InboxTransport {
        override suspend fun deleteSubscription(server: ServerEntity) = false

        override suspend fun fetchInboxPage(server: ServerEntity, cursor: String, limit: Int) =
            InboxPageResult(page = null, invalidCursor = false)

        override suspend fun fetchNotification(server: ServerEntity, notificationId: String, fromServer: String) =
            NotificationFetchResult(state = null, notFound = true)

        override suspend fun confirmSubscription(server: ServerEntity, installationId: String, version: Int, challenge: String) = false

        override suspend fun registerSubscription(
            server: ServerEntity,
            installationId: String,
            deliveryType: String,
            endpoint: String,
            p256dh: String,
            auth: String
        ) = null
    }

    private fun repository() = InboxRepository(
        context = ApplicationProvider.getApplicationContext(),
        notificationDao = notificationDao,
        serverDao = serverDao,
        transport = NoopTransport()
    )

    private val action = ActionItem("a1", "Open", ActionKind.BUTTON)

    private fun entity(revision: String = "1", isRead: Boolean = false, isResponded: Boolean = false) =
        NotificationEntity(
            id = 1,
            createdAt = 100L,
            fromServer = "srv-1",
            fromApp = "app",
            title = "Title",
            body = "Body",
            actions = listOf(action),
            isRead = isRead,
            isResponded = isResponded,
            revision = revision
        )

    private fun state(responded: Boolean, title: String = "Updated") = NotificationStateDto(
        notification = NotificationDto(id = "1", title = title, body = "Body"),
        responded = responded
    )

    @Test
    fun `merge preserves the local read flag and empties actions once responded`() = runBlocking {
        notificationDao.insertNotification(entity(isRead = true))

        NotificationStateApplier.apply(notificationDao, entity(revision = "2"), state(responded = true))

        val stored = notificationDao.getNotificationDirect("srv-1", 1)!!
        assertTrue(stored.isRead)
        assertTrue(stored.isResponded)
        assertTrue(stored.actions.isEmpty())
    }

    @Test
    fun `merge keeps actions while the notification is still unanswered`() = runBlocking {
        notificationDao.insertNotification(entity(isRead = true))

        NotificationStateApplier.apply(notificationDao, entity(revision = "2"), state(responded = false))

        val stored = notificationDao.getNotificationDirect("srv-1", 1)!!
        assertTrue(stored.isRead)
        assertFalse(stored.isResponded)
        assertTrue(stored.actions.isNotEmpty())
    }

    @Test
    fun `state_update for an unknown notification is ignored`() = runBlocking {
        val applied = repository().applyStateUpdate(
            DecodeResult.StateUpdate(state(responded = true), "2", "srv-1", "conn-1")
        )
        assertFalse(applied)
        assertTrue(notificationDao.getTotalCount() == 0)
    }

    @Test
    fun `state_update with a newer revision merges without alerting`() = runBlocking {
        notificationDao.insertNotification(entity(revision = "1"))

        val applied = repository().applyStateUpdate(
            DecodeResult.StateUpdate(state(responded = true), "2", "srv-1", "conn-1")
        )

        assertTrue(applied)
        val stored = notificationDao.getNotificationDirect("srv-1", 1)!!
        assertTrue(stored.isResponded)
        assertTrue(stored.actions.isEmpty())
    }

    @Test
    fun `state_update with an older revision is rejected`() = runBlocking {
        notificationDao.insertNotification(entity(revision = "5"))

        val applied = repository().applyStateUpdate(
            DecodeResult.StateUpdate(state(responded = true), "3", "srv-1", "conn-1")
        )

        assertFalse(applied)
        assertFalse(notificationDao.getNotificationDirect("srv-1", 1)!!.isResponded)
    }
}
