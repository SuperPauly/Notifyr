package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class ServerInfoClientTest {

    private val baseUrl = "https://notifyr.example.invalid/"

    private fun client(serverId: String, key: String, contract: String): OkHttpClient =
        FakeHttp.clientReturning(
            200,
            """{"server_id":"$serverId","vapid_public_key":"$key","contract":"$contract"}"""
        )

    @Test
    fun `valid server-info succeeds`() {
        val key = FakeHttp.base64Url(FakeHttp.p256Key())
        val result = ServerInfoClient(client("11111111-2222-4333-8444-555555555555", key, "notifications.v2"), baseUrl).fetch()
        assertTrue(result is ServerInfoResult.Success)
    }

    @Test
    fun `non-uuid server_id is rejected`() {
        val key = FakeHttp.base64Url(FakeHttp.p256Key())
        val result = ServerInfoClient(client("not-a-uuid", key, "notifications.v2"), baseUrl).fetch()
        assertEquals(ServerInfoError.INVALID_SERVER_ID, (result as ServerInfoResult.Failure).error)
    }

    @Test
    fun `64-byte vapid key is rejected`() {
        val key = FakeHttp.base64Url(FakeHttp.p256Key(size = 64))
        val result = ServerInfoClient(client("11111111-2222-4333-8444-555555555555", key, "notifications.v2"), baseUrl).fetch()
        assertEquals(ServerInfoError.INVALID_VAPID_KEY, (result as ServerInfoResult.Failure).error)
    }

    @Test
    fun `65-byte key without uncompressed prefix is rejected`() {
        val key = FakeHttp.base64Url(FakeHttp.p256Key(first = 0x02))
        val result = ServerInfoClient(client("11111111-2222-4333-8444-555555555555", key, "notifications.v2"), baseUrl).fetch()
        assertEquals(ServerInfoError.INVALID_VAPID_KEY, (result as ServerInfoResult.Failure).error)
    }

    @Test
    fun `contract mismatch is rejected`() {
        val key = FakeHttp.base64Url(FakeHttp.p256Key())
        val result = ServerInfoClient(client("11111111-2222-4333-8444-555555555555", key, "notifications.v1"), baseUrl).fetch()
        assertEquals(ServerInfoError.CONTRACT_MISMATCH, (result as ServerInfoResult.Failure).error)
    }

    @Test
    fun `http error is reported`() {
        val result = ServerInfoClient(FakeHttp.clientReturning(500, """{"error":"internal_error"}"""), baseUrl).fetch()
        assertEquals(ServerInfoError.HTTP, (result as ServerInfoResult.Failure).error)
    }

    @Test
    fun `network failure is unreachable`() {
        val result = ServerInfoClient(FakeHttp.failingClient(), baseUrl).fetch()
        assertEquals(ServerInfoError.UNREACHABLE, (result as ServerInfoResult.Failure).error)
    }

    @Test
    fun `discovery sends no authorization header`() {
        val captured = AtomicReference<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                captured.set(chain.request())
                FakeHttp.respond(chain, 200, """{"server_id":"x","vapid_public_key":"y","contract":"z"}""")
            }
            .build()
        ServerInfoClient(client, baseUrl).fetch()
        assertNull(captured.get().header("Authorization"))
    }
}
