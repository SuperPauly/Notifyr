package com.example.data.remote

import com.example.data.remote.model.ServerInfoDto
import com.squareup.moshi.Moshi
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

sealed interface ServerInfoResult {
    data class Success(val info: ServerInfoDto) : ServerInfoResult
    data class Failure(val error: ServerInfoError, val detail: String) : ServerInfoResult
}

enum class ServerInfoError {
    UNREACHABLE,
    HTTP,
    MALFORMED,
    INVALID_SERVER_ID,
    INVALID_VAPID_KEY,
    CONTRACT_MISMATCH
}

/**
 * Unauthenticated discovery via `GET /v1/server-info`. No Authorization header is sent.
 */
class ServerInfoClient(
    private val callFactory: Call.Factory,
    baseUrl: String,
    moshi: Moshi = Moshi.Builder().build()
) {
    private val baseUrl: HttpUrl = baseUrl.toHttpUrl()
    private val adapter = moshi.adapter(ServerInfoDto::class.java)

    fun fetch(): ServerInfoResult {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments("v1/server-info").build())
            .get()
            .build()
        return try {
            callFactory.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    return ServerInfoResult.Failure(ServerInfoError.HTTP, "HTTP ${response.code}")
                }
                if (body.isNullOrBlank()) {
                    return ServerInfoResult.Failure(ServerInfoError.MALFORMED, "Empty response body")
                }
                val dto = adapter.fromJson(body)
                    ?: return ServerInfoResult.Failure(ServerInfoError.MALFORMED, "Unparseable response body")
                validate(dto)
            }
        } catch (e: IOException) {
            ServerInfoResult.Failure(ServerInfoError.UNREACHABLE, e.message ?: "Network failure")
        } catch (e: RuntimeException) {
            ServerInfoResult.Failure(ServerInfoError.MALFORMED, e.message ?: "Malformed response")
        }
    }

    private fun validate(dto: ServerInfoDto): ServerInfoResult {
        if (!isUuid(dto.serverId)) {
            return ServerInfoResult.Failure(ServerInfoError.INVALID_SERVER_ID, "server_id is not a UUID")
        }
        if (!isValidP256Point(dto.vapidPublicKey)) {
            return ServerInfoResult.Failure(
                ServerInfoError.INVALID_VAPID_KEY,
                "vapid_public_key is not a 65-byte uncompressed P-256 point"
            )
        }
        if (dto.contract != CONTRACT) {
            return ServerInfoResult.Failure(ServerInfoError.CONTRACT_MISMATCH, "Unsupported contract: ${dto.contract}")
        }
        return ServerInfoResult.Success(dto)
    }

    companion object {
        const val CONTRACT = "notifications.v2"

        private val UUID_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun isUuid(value: String): Boolean = UUID_REGEX.matches(value)

        fun isValidP256Point(base64Url: String): Boolean {
            val bytes = Base64Url.decode(base64Url) ?: return false
            return bytes.size == 65 && bytes[0] == 0x04.toByte()
        }
    }
}
