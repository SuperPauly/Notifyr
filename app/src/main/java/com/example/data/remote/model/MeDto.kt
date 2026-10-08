package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MeDto(
    val scope: String,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "app_id") val appId: String? = null
)
