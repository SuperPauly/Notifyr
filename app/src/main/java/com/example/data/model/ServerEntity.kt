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

    @ColumnInfo(name = "connection_status")
    val connectionStatus: String = "Connected", // Honest connection status

    @ColumnInfo(name = "last_sync_time")
    val lastSyncTime: Long = System.currentTimeMillis(),

    // UnifiedPush Registration State
    @ColumnInfo(name = "up_distributor")
    val upDistributor: String? = null, // Selected distributor package name (e.g. "org.unifiedpush.distributor")

    @ColumnInfo(name = "up_endpoint")
    val upEndpoint: String? = null, // Push gateway endpoint URL

    @ColumnInfo(name = "up_status")
    val upStatus: String = "Not Registered" // e.g. "Registered with distributor (Awaiting server integration)"
)
