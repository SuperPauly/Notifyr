package com.example.data.transport

import com.example.data.model.ServerEntity
import com.example.data.remote.ApiFactory
import com.example.data.remote.model.ConfirmSubscriptionRequest
import com.example.data.remote.model.InboxPageDto
import com.example.data.remote.model.NotificationStateDto
import com.example.data.remote.model.SubscriptionRequestDto
import com.example.data.remote.model.SubscriptionStatusDto
import com.example.data.security.KeystoreManager
import okhttp3.OkHttpClient
import retrofit2.Response

/** Result of one inbox page fetch. [invalidCursor] is true only for a server `invalid_cursor` 422. */
data class InboxPageResult(
    val page: InboxPageDto?,
    val invalidCursor: Boolean,
    val error: Throwable? = null
) {
    val isSuccess: Boolean get() = page != null
}

/** Result of one single-notification fetch. [notFound] is true for an unknown id. */
data class NotificationFetchResult(
    val state: NotificationStateDto?,
    val notFound: Boolean,
    val error: Throwable? = null
) {
    val isSuccess: Boolean get() = state != null
}

/**
 * Reads the notifications.v2 inbox surface: `GET /v1/inbox` for paging and
 * `GET /v1/notifications/{id}?from_server=` for reference resolution.
 */
interface InboxTransport {
    suspend fun fetchInboxPage(server: ServerEntity, cursor: String, limit: Int): InboxPageResult
    suspend fun fetchNotification(
        server: ServerEntity,
        notificationId: String,
        fromServer: String
    ): NotificationFetchResult

    /** Answers a registration challenge: `POST /v1/subscriptions/{id}/confirm`. */
    suspend fun confirmSubscription(
        server: ServerEntity,
        installationId: String,
        version: Int,
        challenge: String
    ): Boolean

    /**
     * Creates or replaces the UnifiedPush subscription for [installationId]:
     * `PUT /v1/subscriptions/{installation_id}`. [endpoint] and the connector-generated
     * [p256dh]/[auth] Web Push key set are forwarded verbatim.
     */
    suspend fun registerSubscription(
        server: ServerEntity,
        installationId: String,
        deliveryType: String,
        endpoint: String,
        p256dh: String,
        auth: String
    ): SubscriptionStatusDto?
}

class ApiInboxTransport(
    private val keystoreManager: KeystoreManager = KeystoreManager(),
    debugLogging: Boolean = false,
    private val baseClient: OkHttpClient = ApiFactory.okHttpClient(debugLogging = debugLogging)
) : InboxTransport {

    override suspend fun fetchInboxPage(
        server: ServerEntity,
        cursor: String,
        limit: Int
    ): InboxPageResult {
        val api = apiFor(server)
        return try {
            val response: Response<InboxPageDto> = api.inbox(cursor = cursor, limit = limit)
            when {
                response.isSuccessful && response.body() != null ->
                    InboxPageResult(page = response.body(), invalidCursor = false)
                response.code() == 422 && response.isInvalidCursor() ->
                    InboxPageResult(page = null, invalidCursor = true)
                else ->
                    InboxPageResult(page = null, invalidCursor = false, error = ResponseDeliveryException(response.code()))
            }
        } catch (e: Exception) {
            InboxPageResult(page = null, invalidCursor = false, error = e)
        }
    }

    override suspend fun fetchNotification(
        server: ServerEntity,
        notificationId: String,
        fromServer: String
    ): NotificationFetchResult {
        val api = apiFor(server)
        return try {
            val response: Response<NotificationStateDto> =
                api.notification(id = notificationId, fromServer = fromServer)
            when {
                response.isSuccessful && response.body() != null ->
                    NotificationFetchResult(state = response.body(), notFound = false)
                response.code() == 404 ->
                    NotificationFetchResult(state = null, notFound = true)
                else ->
                    NotificationFetchResult(state = null, notFound = false, error = ResponseDeliveryException(response.code()))
            }
        } catch (e: Exception) {
            NotificationFetchResult(state = null, notFound = false, error = e)
        }
    }

    override suspend fun confirmSubscription(
        server: ServerEntity,
        installationId: String,
        version: Int,
        challenge: String
    ): Boolean {
        val api = apiFor(server)
        return try {
            val response: Response<SubscriptionStatusDto> = api.confirmSubscription(
                installationId = installationId,
                request = ConfirmSubscriptionRequest(version = version, challenge = challenge)
            )
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun registerSubscription(
        server: ServerEntity,
        installationId: String,
        deliveryType: String,
        endpoint: String,
        p256dh: String,
        auth: String
    ): SubscriptionStatusDto? {
        val api = apiFor(server)
        return try {
            val response: Response<SubscriptionStatusDto> = api.registerSubscription(
                installationId = installationId,
                request = SubscriptionRequestDto(
                    deliveryType = deliveryType,
                    endpoint = endpoint,
                    p256dh = p256dh,
                    auth = auth
                )
            )
            response.body().takeIf { response.isSuccessful }
        } catch (e: Exception) {
            null
        }
    }

    private fun apiFor(server: ServerEntity) = ApiFactory.create(
        baseUrl = server.baseUrl,
        bearerProvider = { keystoreManager.decrypt(server.encryptedAuthToken) },
        baseClient = baseClient
    )

    private fun Response<*>.isInvalidCursor(): Boolean {
        val body = errorBody()?.string() ?: return false
        return body.contains("invalid_cursor")
    }
}
