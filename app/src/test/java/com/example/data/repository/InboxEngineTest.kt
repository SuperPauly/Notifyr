package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NotificationDao
import com.example.data.local.NotifyrDatabase
import com.example.data.local.ServerDao
import com.example.data.model.ServerEntity
import com.example.data.remote.model.InboxItemDto
import com.example.data.remote.model.InboxPageDto
import com.example.data.remote.model.NotificationDto
import com.example.data.remote.model.NotificationStateDto
import com.example.data.transport.InboxPageResult
import com.example.data.transport.InboxTransport
import com.example.data.transport.NotificationFetchResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Drives [InboxRepository.refresh] against a scripted [InboxTransport]: paging, cursor
 * persistence, empty-page retention, and the `invalid_cursor` self-heal.
 */
@RunWith(RobolectricTestRunner::class)
class InboxEngineTest {

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

    private class FakeTransport(private val pages: ArrayDeque<InboxPageResult>) : InboxTransport {
        val requestedCursors = mutableListOf<String>()

        override suspend fun fetchInboxPage(
            server: ServerEntity,
            cursor: String,
            limit: Int
        ): InboxPageResult {
            requestedCursors.add(cursor)
            return pages.removeFirst()
        }

        override suspend fun fetchNotification(
            server: ServerEntity,
            notificationId: String,
            fromServer: String
        ): NotificationFetchResult = NotificationFetchResult(state = null, notFound = true)

        override suspend fun confirmSubscription(
            server: ServerEntity,
            installationId: String,
            version: Int,
            challenge: String
        ): Boolean = false

        override suspend fun registerSubscription(
            server: ServerEntity,
            installationId: String,
            deliveryType: String,
            endpoint: String,
            p256dh: String,
            auth: String
        ) = null
    }

    private fun server(cursor: String = "0") = ServerEntity(
        connectionId = "conn-1",
        serverId = "srv-1",
        displayName = "Prod",
        baseUrl = "https://srv.example",
        inboxCursor = cursor
    )

    private fun item(id: Long, revision: String) = InboxItemDto(
        revision = revision,
        state = NotificationStateDto(
            notification = NotificationDto(
                id = id.toString(),
                title = "Title",
                body = "Body",
                createdAt = "2025-03-01T12:00:00Z"
            ),
            responded = false
        )
    )

    private fun page(items: List<InboxItemDto>, cursor: String, hasMore: Boolean) =
        InboxPageResult(page = InboxPageDto(items = items, cursor = cursor, hasMore = hasMore), invalidCursor = false)

    private fun invalidCursor() = InboxPageResult(page = null, invalidCursor = true)

    private fun repository(transport: InboxTransport) = InboxRepository(
        context = ApplicationProvider.getApplicationContext(),
        notificationDao = notificationDao,
        serverDao = serverDao,
        transport = transport
    )

    @Test
    fun `refresh pages until has_more is false and persists the final cursor`() = runBlocking {
        val server = server()
        serverDao.insertServer(server)
        val transport = FakeTransport(
            ArrayDeque(
                listOf(
                    page(listOf(item(1, "1")), cursor = "1", hasMore = true),
                    page(listOf(item(2, "2")), cursor = "2", hasMore = false)
                )
            )
        )

        repository(transport).refresh(server)

        assertEquals(listOf("0", "1"), transport.requestedCursors)
        assertEquals("2", serverDao.getCursor("conn-1"))
        assertEquals(2, notificationDao.getTotalCount())
    }

    @Test
    fun `empty page retains the stored cursor`() = runBlocking {
        val server = server(cursor = "5")
        serverDao.insertServer(server)
        val transport = FakeTransport(ArrayDeque(listOf(page(emptyList(), cursor = "5", hasMore = false))))

        repository(transport).refresh(server)

        assertEquals("5", serverDao.getCursor("conn-1"))
        assertEquals(0, notificationDao.getTotalCount())
    }

    @Test
    fun `invalid_cursor resets to zero and retries once`() = runBlocking {
        val server = server(cursor = "9")
        serverDao.insertServer(server)
        val transport = FakeTransport(
            ArrayDeque(
                listOf(
                    invalidCursor(),
                    page(listOf(item(1, "1")), cursor = "1", hasMore = false)
                )
            )
        )

        repository(transport).refresh(server)

        assertEquals(listOf("9", "0"), transport.requestedCursors)
        assertEquals("1", serverDao.getCursor("conn-1"))
    }

    @Test
    fun `second invalid_cursor stops without looping`() = runBlocking {
        val server = server(cursor = "9")
        serverDao.insertServer(server)
        val transport = FakeTransport(ArrayDeque(listOf(invalidCursor(), invalidCursor())))

        repository(transport).refresh(server)

        assertEquals(listOf("9", "0"), transport.requestedCursors)
        assertEquals("0", serverDao.getCursor("conn-1"))
    }

    @Test
    fun `replayed older revision does not overwrite newer local state`() = runBlocking {
        val server = server()
        serverDao.insertServer(server)
        notificationDao.insertNotification(
            com.example.data.model.NotificationEntity(
                id = 1,
                createdAt = 100L,
                fromServer = "srv-1",
                fromApp = "app",
                title = "Newer",
                body = "Body",
                revision = "5"
            )
        )
        val transport = FakeTransport(ArrayDeque(listOf(page(listOf(item(1, "3")), cursor = "3", hasMore = false))))

        repository(transport).refresh(server)

        assertEquals("5", notificationDao.revisionOf("srv-1", 1))
    }
}
