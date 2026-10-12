package com.example.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.NotificationEntity
import com.example.data.model.ServerEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the Room-side contracts the inbox engine depends on: page writes, cursor advance,
 * and revision lookups. Runs against a real in-memory database so the `@Transaction` method
 * and `MAX(revision)` query are exercised for real.
 */
@RunWith(RobolectricTestRunner::class)
class InboxPersistenceTest {

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

    private fun server(connectionId: String = "conn-1") = ServerEntity(
        connectionId = connectionId,
        serverId = "srv-1",
        displayName = "Prod",
        baseUrl = "https://srv.example"
    )

    private fun entity(id: Long, revision: String = "1") = NotificationEntity(
        id = id,
        createdAt = 100L,
        fromServer = "srv-1",
        fromApp = "app",
        title = "Title",
        body = "Body",
        revision = revision
    )

    @Test
    fun `page write and cursor advance commit together`() = runBlocking {
        serverDao.insertServer(server())

        notificationDao.applyPageAndAdvanceCursor(
            connectionId = "conn-1",
            items = listOf(entity(1, "3"), entity(2, "4")),
            cursor = "4"
        )

        assertEquals(2, notificationDao.getTotalCount())
        assertEquals("4", serverDao.getCursor("conn-1"))
    }

    @Test
    fun `null connection id writes the page without touching any cursor`() = runBlocking {
        serverDao.insertServer(server())

        notificationDao.applyPageAndAdvanceCursor(
            connectionId = null,
            items = listOf(entity(1)),
            cursor = "9"
        )

        assertEquals(1, notificationDao.getTotalCount())
        assertEquals("0", serverDao.getCursor("conn-1"))
    }

    @Test
    fun `revisionOf returns the stored revision and null for a missing row`() = runBlocking {
        notificationDao.insertNotification(entity(1, "7"))

        assertEquals("7", notificationDao.revisionOf("srv-1", 1))
        assertNull(notificationDao.revisionOf("srv-1", 999))
    }

    @Test
    fun `highestRevision is null when the server has no rows`() = runBlocking {
        assertNull(notificationDao.highestRevision("srv-1"))
        notificationDao.insertNotification(entity(1, "12"))
        assertEquals("12", notificationDao.highestRevision("srv-1"))
    }
}
