package com.example.data.transport

import com.example.data.model.OutboxEntity
import com.example.data.model.ServerEntity
import com.example.data.remote.ApiFactory
import com.example.data.remote.model.RespondRequestDto
import com.example.data.security.KeystoreManager
import okhttp3.OkHttpClient
import retrofit2.Response

/**
 * Delivers queued interactive responses through `POST /v1/responses`.
 *
 * The bearer token is decrypted from the Keystore at send time and injected per request by the
 * OkHttp interceptor built in [ApiFactory]; it is never persisted or logged. Only 2xx is success;
 * 4xx are terminal (the outbox maps them to a permanent failure) and 5xx/IO are retryable.
 */
class ApiResponseTransport(
    private val keystoreManager: KeystoreManager = KeystoreManager(),
    debugLogging: Boolean = false,
    baseClient: OkHttpClient = ApiFactory.okHttpClient(debugLogging = debugLogging)
) : NotificationResponseTransport {

    private val callFactory: OkHttpClient = baseClient

    override suspend fun deliverResponse(
        server: ServerEntity,
        response: OutboxEntity
    ): Result<Unit> {
        val token = keystoreManager.decrypt(server.encryptedAuthToken)
        val api = ApiFactory.create(
            baseUrl = server.baseUrl,
            bearerProvider = { token },
            baseClient = callFactory
        )
        return try {
            val result: Response<*> = api.respond(
                RespondRequestDto(
                    responseId = response.responseId,
                    notificationId = response.notificationId.toString(),
                    actionId = response.actionId,
                    text = response.replyText,
                    fromServer = response.fromServer
                )
            )
            if (result.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(ResponseDeliveryException(result.code()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/** Non-2xx delivery outcome; [code] distinguishes terminal 4xx from retryable 5xx. */
class ResponseDeliveryException(val code: Int, message: String = "HTTP $code") : Exception(message) {
    val isRetryable: Boolean get() = code >= 500
}
