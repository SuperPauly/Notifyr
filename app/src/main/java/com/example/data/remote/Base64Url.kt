package com.example.data.remote

import java.io.ByteArrayOutputStream

/**
 * Minimal base64url decoder (RFC 4648 §5). Hand-rolled to stay API-24 compatible
 * and usable from plain JVM unit tests; padding is tolerated and ignored.
 */
object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun decode(value: String): ByteArray? {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (ch in value) {
            if (ch == '=') break
            val index = ALPHABET.indexOf(ch)
            if (index < 0) return null
            buffer = (buffer shl 6) or index
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
