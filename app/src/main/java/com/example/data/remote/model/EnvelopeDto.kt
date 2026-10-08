package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Push envelope delivered by the UnifiedPush distributor.
 *
 * Exactly one of [state], [reference] or the challenge fields is present, selected by [kind]:
 * - `notification` / `state_update` -> [state]
 * - `notification` (oversized payload) -> [reference]
 * - `registration_challenge` -> [challenge] + [visibleNotification]
 */
@JsonClass(generateAdapter = true)
data class EnvelopeDto(
    val version: Int,
    val revision: String? = null,
    val kind: EnvelopeKind,
    val state: NotificationStateDto? = null,
    val reference: NotificationReferenceDto? = null,
    @Json(name = "installation_id") val installationId: String? = null,
    @Json(name = "subscription_version") val subscriptionVersion: Int? = null,
    val challenge: String? = null,
    @Json(name = "from_server") val fromServer: String? = null,
    @Json(name = "visible_notification") val visibleNotification: VisibleNotificationDto? = null
)

@JsonClass(generateAdapter = false)
enum class EnvelopeKind {
    @Json(name = "notification") NOTIFICATION,
    @Json(name = "state_update") STATE_UPDATE,
    @Json(name = "registration_challenge") REGISTRATION_CHALLENGE
}

@JsonClass(generateAdapter = true)
data class NotificationStateDto(
    val notification: NotificationDto,
    val responded: Boolean
)

/** Present only when the encoded state exceeded the server payload budget. */
@JsonClass(generateAdapter = true)
data class NotificationReferenceDto(
    @Json(name = "from_server") val fromServer: String,
    @Json(name = "notification_id") val notificationId: String
)

@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: String,
    val title: String,
    val body: String = "",
    val actions: List<ActionDto> = emptyList(),
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "open_path") val openPath: String = "",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "from_server") val fromServer: String = "",
    @Json(name = "from_app") val fromApp: String = ""
)

@JsonClass(generateAdapter = true)
data class ActionDto(
    val id: String,
    val label: String,
    val kind: ActionKindDto
)

@JsonClass(generateAdapter = false)
enum class ActionKindDto {
    @Json(name = "BUTTON") BUTTON,
    @Json(name = "TEXT_REPLY") TEXT_REPLY
}

/** Rendered locally to satisfy `userVisibleOnly` for a registration challenge. */
@JsonClass(generateAdapter = true)
data class VisibleNotificationDto(
    val title: String,
    val body: String
)
