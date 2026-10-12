package com.example.data.remote

import com.example.data.model.ServerEntity
import com.example.data.remote.model.ConfirmSubscriptionRequest
import com.example.data.remote.model.SubscriptionRequestDto
import com.example.data.remote.model.SubscriptionStatusDto
import com.example.data.security.KeystoreManager
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Response
import java.io.IOException

sealed interface SubscriptionResult {
    data class Success(val subscription: SubscriptionStatusDto?) : SubscriptionResult
    data class Failure(val status: Int?, val code: String) : SubscriptionResult
}

class SubscriptionClient(
    private val apiFor: (ServerEntity) -> NotifyrApi = { server ->
        ApiFactory.create(server.baseUrl, { KeystoreManager().decrypt(server.encryptedAuthToken) })
    }
) {
    suspend fun register(server: ServerEntity, installationId: String, request: SubscriptionRequestDto) =
        execute { apiFor(server).registerSubscription(installationId, request) }

    suspend fun confirm(server: ServerEntity, installationId: String, version: Int, challenge: String) =
        execute { apiFor(server).confirmSubscription(installationId, ConfirmSubscriptionRequest(version, challenge)) }

    suspend fun delete(server: ServerEntity, installationId: String): SubscriptionResult = try {
        val response = apiFor(server).deleteSubscription(installationId)
        if (response.code() == 204) SubscriptionResult.Success(null) else failure(response)
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        SubscriptionResult.Failure(null, "unreachable")
    }

    private suspend fun execute(call: suspend () -> Response<SubscriptionStatusDto>): SubscriptionResult = try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body != null) SubscriptionResult.Success(body) else failure(response)
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        SubscriptionResult.Failure(null, "unreachable")
    }

    private fun failure(response: Response<*>): SubscriptionResult.Failure {
        val body = response.errorBody()?.string().orEmpty()
        val code = listOf("challenge_not_current", "challenge_expired", "invalid_challenge",
            "subscription_not_found", "subscription_not_owned", "endpoint_already_registered")
            .firstOrNull { body.contains("\"$it\"") } ?: "http_${response.code()}"
        return SubscriptionResult.Failure(response.code(), code)
    }
}
