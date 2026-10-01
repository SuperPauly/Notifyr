package com.example.data.transport

import com.example.data.model.NotificationEntity
import com.example.data.model.ServerEntity

/**
 * Transport interface for receiving notifications from remote servers.
 * Implementations handle the protocol details (e.g. gRPC, Protobuf, WebSockets, or HTTP polling).
 */
interface NotificationDeliveryTransport {
    suspend fun fetchNotifications(server: ServerEntity): Result<List<NotificationEntity>>
    suspend fun testConnection(server: ServerEntity): Result<String>
}
