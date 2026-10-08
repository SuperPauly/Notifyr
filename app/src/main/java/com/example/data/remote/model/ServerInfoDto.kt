package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServerInfoDto(
    @Json(name = "server_id") val serverId: String,
    @Json(name = "vapid_public_key") val vapidPublicKey: String,
    @Json(name = "contract") val contract: String
)
