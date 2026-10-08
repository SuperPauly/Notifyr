package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request bodies for the notifications.v2 routes. Field names mirror `contract.py` exactly;
 * unknown properties are rejected server-side, so do not add extras.
 */

@JsonClass(generateAdapter = true)
data class PublishRequestDto(
    @Json(name = "request_id") val requestId: String,
    @Json(name = "recipient_user_ids") val recipientUserIds: List<String>,
    val notification: NotificationDto
)

@JsonClass(generateAdapter = true)
data class RespondRequestDto(
    @Json(name = "response_id") val responseId: String,
    @Json(name = "notification_id") val notificationId: String,
    @Json(name = "action_id") val actionId: String,
    val text: String? = null,
    @Json(name = "from_server") val fromServer: String
)

@JsonClass(generateAdapter = true)
data class SubscriptionRequestDto(
    @Json(name = "delivery_type") val deliveryType: String,
    val endpoint: String,
    val p256dh: String,
    val auth: String
)

@JsonClass(generateAdapter = true)
data class ConfirmSubscriptionRequest(
    val version: Int,
    val challenge: String
)

@JsonClass(generateAdapter = true)
data class SubscriptionStatusDto(
    @Json(name = "installation_id") val installationId: String,
    val version: Int,
    val status: String
)

@JsonClass(generateAdapter = true)
data class ResponseReceiptDto(
    @Json(name = "response_id") val responseId: String,
    @Json(name = "accepted_at") val acceptedAt: String
)
