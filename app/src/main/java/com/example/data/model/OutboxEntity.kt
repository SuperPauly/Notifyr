package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox_responses")
data class OutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "response_id")
    val responseId: String, // UUID generated and persisted before any send attempt

    @ColumnInfo(name = "notification_id")
    val notificationId: Long, // Kotlin Long, protobuf int64

    @ColumnInfo(name = "from_server")
    val fromServer: String, // Stable server identifier

    @ColumnInfo(name = "action_id")
    val actionId: String, // Action identifier

    @ColumnInfo(name = "action_label")
    val actionLabel: String,

    @ColumnInfo(name = "action_kind")
    val actionKind: ActionKind,

    @ColumnInfo(name = "reply_text")
    val replyText: String? = null, // Optional text. Omitted for button actions, nonblank for text replies

    @ColumnInfo(name = "connection_id")
    val connectionId: String, // Local connection/account identity for routing

    @ColumnInfo(name = "account_identity")
    val accountIdentity: String? = null,

    @ColumnInfo(name = "notification_title")
    val notificationTitle: String,

    @ColumnInfo(name = "from_app")
    val fromApp: String,

    @ColumnInfo(name = "status")
    val status: OutboxStatus = OutboxStatus.QUEUED,

    @ColumnInfo(name = "is_simulated")
    val isSimulated: Boolean = false, // Demo responses labelled "Simulated"

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)
