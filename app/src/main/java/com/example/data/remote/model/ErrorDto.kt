package com.example.data.remote.model

import com.squareup.moshi.JsonClass

/** Every error response is exactly `{"error":"<stable_code>"}`. */
@JsonClass(generateAdapter = true)
data class ErrorDto(val error: String)
