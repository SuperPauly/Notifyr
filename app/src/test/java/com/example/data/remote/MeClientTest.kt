package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class MeClientTest {

    private val baseUrl = "https://notifyr.example.invalid/"
    private val token = "super-secret-bearer-token-abc123"

    @Test
    fun `recipient identity succeeds`() {
        val result = MeClient(FakeHttp.clientReturning(200, """{"scope":"recipient","user_id":"alice"}"""), baseUrl)
            .fetch(token)
        assertTrue(result is MeResult.Success)
    }

    @Test
    fun `401 maps to auth required`() {
        val result = MeClient(FakeHttp.clientReturning(401, """{"error":"unauthorized"}"""), baseUrl).fetch(token)
        assertEquals(MeError.AUTH_REQUIRED, (result as MeResult.Failure).error)
    }

    @Test
    fun `publisher token is rejected`() {
        val result = MeClient(FakeHttp.clientReturning(200, """{"scope":"publisher","app_id":"monitor"}"""), baseUrl)
            .fetch(token)
        assertEquals(MeError.SCOPE_NOT_RECIPIENT, (result as MeResult.Failure).error)
    }

    @Test
    fun `missing user_id is rejected`() {
        val result = MeClient(FakeHttp.clientReturning(200, """{"scope":"recipient"}"""), baseUrl).fetch(token)
        assertEquals(MeError.INVALID_IDENTITY, (result as MeResult.Failure).error)
    }

    @Test
    fun `bearer token never appears in any result string`() {
        val cases = listOf(
            MeClient(FakeHttp.clientReturning(401, """{"error":"unauthorized"}"""), baseUrl).fetch(token),
            MeClient(FakeHttp.clientReturning(500, """{"error":"internal_error"}"""), baseUrl).fetch(token),
            MeClient(FakeHttp.clientReturning(200, """{"scope":"publisher","app_id":"monitor"}"""), baseUrl).fetch(token),
            MeClient(FakeHttp.failingClient("connection reset by peer"), baseUrl).fetch(token)
        )
        for (result in cases) {
            assertFalse("token leaked in result: $result", result.toString().contains(token))
        }
    }

    @Test
    fun `me request carries the bearer token`() {
        val captured = AtomicReference<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                captured.set(chain.request())
                FakeHttp.respond(chain, 200, """{"scope":"recipient","user_id":"alice"}""")
            }
            .build()
        MeClient(client, baseUrl).fetch(token)
        assertEquals("Bearer $token", captured.get().header("Authorization"))
    }
}
