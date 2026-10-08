package com.example.data.remote

import com.example.data.remote.model.ConfirmSubscriptionRequest
import com.example.data.remote.model.InboxPageDto
import com.example.data.remote.model.MeDto
import com.example.data.remote.model.NotificationStateDto
import com.example.data.remote.model.PublishRequestDto
import com.example.data.remote.model.RespondRequestDto
import com.example.data.remote.model.ResponseReceiptDto
import com.example.data.remote.model.ServerInfoDto
import com.example.data.remote.model.SubscriptionRequestDto
import com.example.data.remote.model.SubscriptionStatusDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Documented notifications.v2 HTTP surface. Authorization is injected per request by the
 * OkHttp interceptor built in [ApiFactory]. `GET /v1/server-info` is handled by
 * [ServerInfoClient] directly because it is the one unauthenticated route.
 */
interface NotifyrApi {

    @GET("v1/me")
    suspend fun me(): Response<MeDto>

    @GET("v1/inbox")
    suspend fun inbox(
        @Query("cursor") cursor: String = "0",
        @Query("limit") limit: Int = 50
    ): Response<InboxPageDto>

    @GET("v1/notifications/{id}")
    suspend fun notification(
        @Path("id") id: String,
        @Query("from_server") fromServer: String
    ): Response<NotificationStateDto>

    @POST("v1/notifications")
    suspend fun publish(@Body request: PublishRequestDto): Response<NotificationStateDto>

    @POST("v1/responses")
    suspend fun respond(@Body request: RespondRequestDto): Response<ResponseReceiptDto>

    @PUT("v1/subscriptions/{installation_id}")
    suspend fun registerSubscription(
        @Path("installation_id") installationId: String,
        @Body request: SubscriptionRequestDto
    ): Response<SubscriptionStatusDto>

    @POST("v1/subscriptions/{installation_id}/confirm")
    suspend fun confirmSubscription(
        @Path("installation_id") installationId: String,
        @Body request: ConfirmSubscriptionRequest
    ): Response<SubscriptionStatusDto>

    @DELETE("v1/subscriptions/{installation_id}")
    suspend fun deleteSubscription(
        @Path("installation_id") installationId: String
    ): Response<Unit>
}
