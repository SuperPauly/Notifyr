package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: adds discovery/contract identity, identity scope, diagnostics, inbox cursor,
 * and UnifiedPush subscription-registration columns to the `servers` table; `revision` to
 * `notifications`; and delivery-receipt / retry bookkeeping to `outbox_responses`.
 *
 * v1 remains a valid, shipped schema; this migration exists ONLY for these additions. No
 * column is dropped or renamed, so no table rebuild is required and no row can be lost.
 *
 * Non-null additions carry a DEFAULT so existing rows backfill deterministically:
 * `servers.inbox_cursor` -> '0', `notifications.revision` -> '0', `outbox_responses.attempt_count` -> 0.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE servers ADD COLUMN vapid_public_key TEXT")
        db.execSQL("ALTER TABLE servers ADD COLUMN contract_version TEXT")
        db.execSQL("ALTER TABLE servers ADD COLUMN identity_scope TEXT")
        db.execSQL("ALTER TABLE servers ADD COLUMN identity_subject TEXT")
        db.execSQL("ALTER TABLE servers ADD COLUMN last_error_code TEXT")
        db.execSQL("ALTER TABLE servers ADD COLUMN inbox_cursor TEXT NOT NULL DEFAULT '0'")
        db.execSQL("ALTER TABLE servers ADD COLUMN installation_id TEXT")
        db.execSQL("ALTER TABLE servers ADD COLUMN subscription_version INTEGER")
        db.execSQL("ALTER TABLE servers ADD COLUMN subscription_status TEXT")
        db.execSQL("ALTER TABLE servers ADD COLUMN subscription_p256dh TEXT")
        db.execSQL("ALTER TABLE servers ADD COLUMN subscription_auth TEXT")
        db.execSQL("ALTER TABLE servers ADD COLUMN connector_package TEXT")

        db.execSQL("ALTER TABLE notifications ADD COLUMN revision TEXT NOT NULL DEFAULT '0'")

        db.execSQL("ALTER TABLE outbox_responses ADD COLUMN receipt_response_id TEXT")
        db.execSQL("ALTER TABLE outbox_responses ADD COLUMN receipt_accepted_at INTEGER")
        db.execSQL("ALTER TABLE outbox_responses ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE outbox_responses ADD COLUMN next_attempt_at INTEGER")
        db.execSQL("ALTER TABLE outbox_responses ADD COLUMN terminal_error_code TEXT")
    }
}

/**
 * v2 -> v3: removes plaintext subscription key columns `subscription_p256dh` and `subscription_auth`
 * from `servers` table. KeyMaterialStore now encrypts these in SharedPreferences via Android Keystore.
 * Requires table rebuild; existing plaintext values are discarded (re-registration required).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE servers_new (
                connection_id TEXT PRIMARY KEY NOT NULL,
                server_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                endpoint TEXT NOT NULL,
                bearer_token TEXT NOT NULL,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                last_sync_time INTEGER NOT NULL DEFAULT 0,
                connection_status TEXT NOT NULL DEFAULT 'unknown',
                vapid_public_key TEXT,
                contract_version TEXT,
                identity_scope TEXT,
                identity_subject TEXT,
                last_error_code TEXT,
                inbox_cursor TEXT NOT NULL DEFAULT '0',
                installation_id TEXT,
                subscription_version INTEGER,
                subscription_status TEXT,
                connector_package TEXT,
                up_distributor TEXT,
                up_endpoint TEXT,
                up_status TEXT NOT NULL DEFAULT 'Not Registered'
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO servers_new SELECT
                connection_id, server_id, display_name, endpoint, bearer_token,
                is_enabled, last_sync_time, connection_status,
                vapid_public_key, contract_version, identity_scope, identity_subject,
                last_error_code, inbox_cursor, installation_id,
                subscription_version, subscription_status, connector_package,
                up_distributor, up_endpoint, up_status
            FROM servers
        """.trimIndent())

        db.execSQL("DROP TABLE servers")
        db.execSQL("ALTER TABLE servers_new RENAME TO servers")
    }
}
