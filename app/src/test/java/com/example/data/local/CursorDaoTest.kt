package com.example.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.NotificationEntity
import com.example.data.model.ServerEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves the rollback guarantee in plan Verify 3: `applyPageAndAdvanceCursor` writes the page and
 * advances the cursor in ONE transaction, so if a mid-page write throws the page and the cursor
 * roll back together — the cursor never advances past data that failed to persist.
 *
 * A `BEFORE INSERT` trigger aborts on the second row, forcing a real `SQLiteConstraintException`
 * partway through the batch (Room's `OnConflictStrategy.REPLACE` means duplicate keys would not
 * fail, so a trigger is the deterministic way to break the write mid-page).
 */
@RunWith(RobolectricTestRunner::class)
class CursorDaoTest {

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
    fun `cursor is unchanged when a mid-page write throws`() = runBlocking {
        serverDao.insertServer(server())
        serverDao.upsertCursor("conn-1", "5")
        assertEquals("5", serverDao.getCursor("conn-1"))

        // Abort the insert of the second row so the batch fails mid-page.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER abort_mid_page BEFORE INSERT ON notifications " +
                "WHEN NEW.id = 2 BEGIN SELECT RAISE(ABORT, 'mid-page write failure'); END"
        )

        var threw = false
        try {
            notificationDao.applyPageAndAdvanceCursor(
                connectionId = "conn-1",
                items = listOf(entity(1, "6"), entity(2, "7")),
                cursor = "7"
            )
        } catch (_: Exception) {
            threw = true
        }

        assertTrue("the mid-page write must fail", threw)
        // Whole transaction rolled back: no page rows, cursor still the pre-write value.
        assertEquals(0, notificationDao.getTotalCount())
        assertEquals("5", serverDao.getCursor("conn-1"))
    }
}
