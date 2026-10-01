package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "notifications",
    primaryKeys = ["from_server", "id"],
    indices = [
        Index(value = ["from_server"]),
        Index(value = ["from_app"]),
        Index(value = ["created_at"]),
        Index(value = ["is_read"]),
        Index(value = ["is_responded"])
    ]
)
data class NotificationEntity(
    @ColumnInfo(name = "id")
    val id: Long, // positive Kotlin Long, corresponding to protobuf int64
    @ColumnInfo(name = "created_at")
    val createdAt: Long, // UTC timestamp epoch millis
    @ColumnInfo(name = "from_server")
    val fromServer: String, // stable server identifier
    @ColumnInfo(name = "from_app")
    val fromApp: String, // sending application identifier
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "actions")
    val actions: List<ActionItem> = emptyList(),
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null, // optional UTC timestamp
    @ColumnInfo(name = "open_path")
    val openPath: String? = null, // optional internal navigation path

    // Client state persistence
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,
    @ColumnInfo(name = "is_responded")
    val isResponded: Boolean = false,
    @ColumnInfo(name = "responded_action_id")
    val respondedActionId: String? = null,
    @ColumnInfo(name = "responded_text")
    val respondedText: String? = null,
    @ColumnInfo(name = "responded_at")
    val respondedAt: Long? = null
) {
    val isExpired: Boolean
        get() = expiresAt != null && expiresAt < System.currentTimeMillis()

    val isAwaitingResponse: Boolean
        get() = actions.isNotEmpty() && !isResponded && !isExpired
}
