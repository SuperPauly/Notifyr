package com.example.data.remote

import com.example.data.remote.model.MeDto
import com.squareup.moshi.Moshi
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

sealed interface MeResult {
    data class Success(val me: MeDto) : MeResult
    data class Failure(val error: MeError, val detail: String) : MeResult
}

enum class MeError {
    UNREACHABLE,
    HTTP,
    MALFORMED,
    AUTH_REQUIRED,
    SCOPE_NOT_RECIPIENT,
    INVALID_IDENTITY
}

/**
 * Bearer identity probe via `GET /v1/me`. Requires a recipient-scoped credential.
 * The bearer token is never echoed into a result, message or exception.
 */
class MeClient(
    private val callFactory: Call.Factory,
    baseUrl: String,
    moshi: Moshi = Moshi.Builder().build()
) {
    private val baseUrl: HttpUrl = baseUrl.toHttpUrl()
    private val adapter = moshi.adapter(MeDto::class.java)

    fun fetch(bearerToken: String): MeResult {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments("v1/me").build())
            .header("Authorization", "Bearer $bearerToken")
            .get()
            .build()
        return try {
            callFactory.newCall(request).execute().use { response ->
                when (response.code) {
                    401 -> return MeResult.Failure(MeError.AUTH_REQUIRED, "Authentication required")
                    403 -> return MeResult.Failure(MeError.SCOPE_NOT_RECIPIENT, "Recipient scope required")
                }
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    return MeResult.Failure(MeError.HTTP, "HTTP ${response.code}")
                }
                if (body.isNullOrBlank()) {
                    return MeResult.Failure(MeError.MALFORMED, "Empty response body")
                }
                val dto = adapter.fromJson(body)
                    ?: return MeResult.Failure(MeError.MALFORMED, "Unparseable response body")
                validate(dto)
            }
        } catch (e: IOException) {
            MeResult.Failure(MeError.UNREACHABLE, sanitize(e.message ?: "Network failure", bearerToken))
        } catch (e: RuntimeException) {
            MeResult.Failure(MeError.MALFORMED, sanitize(e.message ?: "Malformed response", bearerToken))
        }
    }

    private fun validate(dto: MeDto): MeResult {
        if (dto.scope != RECIPIENT_SCOPE) {
            return MeResult.Failure(MeError.SCOPE_NOT_RECIPIENT, "Credential scope is '${dto.scope}'")
        }
        if (dto.userId.isNullOrBlank()) {
            return MeResult.Failure(MeError.INVALID_IDENTITY, "Recipient identity missing user_id")
        }
        return MeResult.Success(dto)
    }

    private fun sanitize(detail: String, bearerToken: String): String =
        if (bearerToken.isNotEmpty()) detail.replace(bearerToken, "[REDACTED]") else detail

    companion object {
        const val RECIPIENT_SCOPE = "recipient"
    }
}
