package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises [MIGRATION_1_2] directly against a hand-built v1 database. A [MigrationTestHelper]
 * would need the KSP-exported `1.json`; issuing the exact v1 `CREATE TABLE` statements here
 * keeps the test runnable before the first schema-exporting build.
 */
@RunWith(RobolectricTestRunner::class)
class Migration_1_2_Test {

    private val v1CreateStatements = listOf(
        """
        CREATE TABLE IF NOT EXISTS `servers` (
            `connection_id` TEXT NOT NULL,
            `server_id` TEXT NOT NULL,
            `display_name` TEXT NOT NULL,
            `base_url` TEXT NOT NULL,
            `account_identity` TEXT,
            `encrypted_auth_token` TEXT,
            `color_hex` INTEGER NOT NULL,
            `is_enabled` INTEGER NOT NULL,
            `connection_status` TEXT NOT NULL,
            `last_sync_time` INTEGER NOT NULL,
            `up_distributor` TEXT,
            `up_endpoint` TEXT,
            `up_status` TEXT NOT NULL,
            PRIMARY KEY(`connection_id`)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `notifications` (
            `id` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL,
            `from_server` TEXT NOT NULL,
            `from_app` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `body` TEXT NOT NULL,
            `actions` TEXT NOT NULL,
            `expires_at` INTEGER,
            `open_path` TEXT,
            `is_read` INTEGER NOT NULL,
            `is_responded` INTEGER NOT NULL,
            `responded_action_id` TEXT,
            `responded_text` TEXT,
            `responded_at` INTEGER,
            PRIMARY KEY(`from_server`, `id`)
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_notifications_from_server` ON `notifications` (`from_server`)",
        "CREATE INDEX IF NOT EXISTS `index_notifications_from_app` ON `notifications` (`from_app`)",
        "CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)",
        "CREATE INDEX IF NOT EXISTS `index_notifications_is_read` ON `notifications` (`is_read`)",
        "CREATE INDEX IF NOT EXISTS `index_notifications_is_responded` ON `notifications` (`is_responded`)",
        """
        CREATE TABLE IF NOT EXISTS `outbox_responses` (
            `response_id` TEXT NOT NULL,
            `notification_id` INTEGER NOT NULL,
            `from_server` TEXT NOT NULL,
            `action_id` TEXT NOT NULL,
            `action_label` TEXT NOT NULL,
            `action_kind` TEXT NOT NULL,
            `reply_text` TEXT,
            `connection_id` TEXT NOT NULL,
            `account_identity` TEXT,
            `notification_title` TEXT NOT NULL,
            `from_app` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `is_simulated` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL,
            `updated_at` INTEGER NOT NULL,
            `error_message` TEXT,
            PRIMARY KEY(`response_id`)
        )
        """
    )

    private fun createV1Database(name: String? = null): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v1CreateStatements.forEach(db::execSQL)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    @Test
    fun `migration preserves every row and backfills new non-null columns`() {
        val db = createV1Database()

        db.execSQL(
            "INSERT INTO servers (connection_id, server_id, display_name, base_url, color_hex, is_enabled, connection_status, last_sync_time, up_status) " +
                "VALUES ('conn-1', 'srv-1', 'Prod', 'https://srv.example', 1, 1, 'CONNECTED', 100, 'Not Registered')"
        )
        db.execSQL(
            "INSERT INTO notifications (id, created_at, from_server, from_app, title, body, actions, is_read, is_responded) " +
                "VALUES (42, 100, 'srv-1', 'app', 'Title', 'Body', '[]', 0, 0)"
        )
        db.execSQL(
            "INSERT INTO outbox_responses (response_id, notification_id, from_server, action_id, action_label, action_kind, connection_id, notification_title, from_app, status, is_simulated, created_at, updated_at) " +
                "VALUES ('resp-sending', 42, 'srv-1', 'a1', 'Open', 'BUTTON', 'conn-1', 'Title', 'app', 'SENDING', 0, 100, 100)"
        )
        db.execSQL(
            "INSERT INTO outbox_responses (response_id, notification_id, from_server, action_id, action_label, action_kind, connection_id, notification_title, from_app, status, is_simulated, created_at, updated_at) " +
                "VALUES ('resp-sim', 43, 'srv-1', 'a2', 'Reply', 'TEXT_REPLY', 'conn-1', 'Title', 'app', 'SIMULATED', 1, 100, 100)"
        )

        MIGRATION_1_2.migrate(db)

        assertEquals(1, count(db, "servers"))
        assertEquals(1, count(db, "notifications"))
        assertEquals(2, count(db, "outbox_responses"))

        db.query("SELECT inbox_cursor FROM servers WHERE connection_id = 'conn-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("0", it.getString(0))
        }
        db.query("SELECT revision FROM notifications WHERE id = 42").use {
            assertTrue(it.moveToFirst())
            assertEquals("0", it.getString(0))
        }
        db.query("SELECT attempt_count, status FROM outbox_responses WHERE response_id = 'resp-sending'").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
            assertEquals("SENDING", it.getString(1))
        }
        db.query("SELECT is_simulated, attempt_count FROM outbox_responses WHERE response_id = 'resp-sim'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertEquals(0, it.getInt(1))
        }

        db.close()
    }

    @Test
    fun `opening a v1 database without the migration throws instead of wiping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "notifyr_migration_guard_test.db"
        context.deleteDatabase(dbName)

        createV1Database(dbName).apply {
            execSQL(
                "INSERT INTO servers (connection_id, server_id, display_name, base_url, color_hex, is_enabled, connection_status, last_sync_time, up_status) " +
                    "VALUES ('conn-1', 'srv-1', 'Prod', 'https://srv.example', 1, 1, 'CONNECTED', 100, 'Not Registered')"
            )
            close()
        }

        val room = Room.databaseBuilder(context, NotifyrDatabase::class.java, dbName)
            .build()
        assertThrows(IllegalStateException::class.java) {
            room.openHelper.writableDatabase
        }
        room.close()

        val reopened = createV1Database(dbName)
        assertEquals(1, count(reopened, "servers"))
        reopened.close()

        context.deleteDatabase(dbName)
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
