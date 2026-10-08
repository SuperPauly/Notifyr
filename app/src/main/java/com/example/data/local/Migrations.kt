package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: adds discovery/contract identity, identity scope, diagnostics, inbox cursor,
 * and UnifiedPush subscription-registration columns to the `servers` table.
 *
 * NOTE: The plan assigns full schema export + Migration_1_2_Test to Phase 3; this minimal
 * migration only exists so Phase 1's entity change does not produce a runtime schema mismatch.
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
    }
}
