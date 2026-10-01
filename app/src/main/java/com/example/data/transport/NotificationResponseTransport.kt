package com.example.data.transport

import com.example.data.model.OutboxEntity
import com.example.data.model.ServerEntity

/**
 * Transport interface for delivering user interactive responses (button clicks or text replies)
 * back to the respective server.
 */
interface NotificationResponseTransport {
    suspend fun deliverResponse(server: ServerEntity, response: OutboxEntity): Result<Unit>
}
