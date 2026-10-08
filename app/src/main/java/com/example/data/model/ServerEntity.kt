package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey
    @ColumnInfo(name = "connection_id")
    val connectionId: String = UUID.randomUUID().toString(), // Local connection identifier

    @ColumnInfo(name = "server_id")
    val serverId: String, // Stable server identifier, when known (e.g. "srv-prod-us")

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "base_url")
    val baseUrl: String,

    @ColumnInfo(name = "account_identity")
    val accountIdentity: String? = null, // Account identity, when configured

    @ColumnInfo(name = "encrypted_auth_token")
    val encryptedAuthToken: String? = null, // Android Keystore-backed encrypted credential

    @ColumnInfo(name = "color_hex")
    val colorHex: Long = 0xFF4F46E5, // Profile color theme

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    // Derived ONLY from real /v1/server-info + /v1/me probe results.
    @ColumnInfo(name = "connection_status")
    val connectionStatus: ConnectionStatus = ConnectionStatus.UNCONNECTED,

    @ColumnInfo(name = "last_sync_time")
    val lastSyncTime: Long = System.currentTimeMillis(),

    // --- Discovery / contract identity (populated from GET /v1/server-info) ---
    @ColumnInfo(name = "vapid_public_key")
    val vapidPublicKey: String? = null, // base64url 65-byte uncompressed P-256 point

    @ColumnInfo(name = "contract_version")
    val contractVersion: String? = null, // e.g. "notifications.v2"

    // --- Bearer identity (populated from GET /v1/me) ---
    @ColumnInfo(name = "identity_scope")
    val identityScope: String? = null, // "recipient" | "publisher"

    @ColumnInfo(name = "identity_subject")
    val identitySubject: String? = null, // user_id or app_id

    // --- Diagnostics ---
    @ColumnInfo(name = "last_error_code")
    val lastErrorCode: String? = null, // stable server error code, e.g. "unauthorized"

    // --- Inbox reconciliation cursor (decimal string) ---
    @ColumnInfo(name = "inbox_cursor", defaultValue = "0")
    val inboxCursor: String = "0",

    // --- UnifiedPush subscription registration state ---
    @ColumnInfo(name = "installation_id")
    val installationId: String? = null, // persistent per-installation UUID

    @ColumnInfo(name = "subscription_version")
    val subscriptionVersion: Int? = null,

    @ColumnInfo(name = "subscription_status")
    val subscriptionStatus: String? = null, // "pending" | "active" | "deleted"

    @ColumnInfo(name = "subscription_p256dh")
    val subscriptionP256dh: String? = null, // base64url uncompressed P-256 key

    @ColumnInfo(name = "subscription_auth")
    val subscriptionAuth: String? = null, // base64url 16-byte auth secret

    @ColumnInfo(name = "connector_package")
    val connectorPackage: String? = null, // UnifiedPush distributor package name

    // --- Legacy UnifiedPush registration state
    @ColumnInfo(name = "up_distributor")
    val upDistributor: String? = null, // Selected distributor package name (e.g. "org.unifiedpush.distributor")

    @ColumnInfo(name = "up_endpoint")
    val upEndpoint: String? = null, // Push gateway endpoint URL

    @ColumnInfo(name = "up_status")
    val upStatus: String = "Not Registered" // e.g. "Registered with distributor (Awaiting server integration)"
)
