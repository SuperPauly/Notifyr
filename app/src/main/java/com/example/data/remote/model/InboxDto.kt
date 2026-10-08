package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** `GET /v1/inbox` page; items are ordered by ascending [InboxItemDto.revision]. */
@JsonClass(generateAdapter = true)
data class InboxPageDto(
    val items: List<InboxItemDto>,
    val cursor: String,
    @Json(name = "has_more") val hasMore: Boolean
)

@JsonClass(generateAdapter = true)
data class InboxItemDto(
    val revision: String,
    val state: NotificationStateDto
)
