package com.example.data.unifiedpush

import android.util.Base64

object EndpointValidation {
    fun isValidEndpoint(value: String): Boolean =
        value.length <= 2048 && value.all { it.code in 0x20..0x7E } &&
            value.startsWith("https://")

    fun isValidPublicKey(value: String): Boolean =
        decodeUrl(value)?.size == 65 && decodeUrl(value)?.firstOrNull() == 0x04.toByte()

    fun isValidAuthSecret(value: String): Boolean = decodeUrl(value)?.size == 16

    private fun decodeUrl(value: String): ByteArray? = try {
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    } catch (_: IllegalArgumentException) {
        null
    }
}
