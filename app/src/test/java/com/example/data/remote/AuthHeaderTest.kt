package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthHeaderTest {

    private val captured = mutableListOf<okhttp3.Request>()

    private fun capturingClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            captured += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("ok")
                .body("""{"scope":"recipient","user_id":"alice"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    @Test
    fun `bearer header present on authenticated route`() {
        val interceptor = ApiFactory.authInterceptor { "secret-token" }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor { chain ->
            captured += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("ok")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        client.newCall(
            okhttp3.Request.Builder().url("https://example.test/v1/me").build()
        ).execute().close()
        assertEquals("Bearer secret-token", captured.single().header("Authorization"))
    }

    @Test
    fun `bearer header absent when no token configured`() {
        val interceptor = ApiFactory.authInterceptor { null }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor { chain ->
            captured.clear()
            captured += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("ok")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        client.newCall(
            okhttp3.Request.Builder().url("https://example.test/v1/me").build()
        ).execute().close()
        assertNull(captured.single().header("Authorization"))
    }

    @Test
    fun `server-info client sends no authorization header`() {
        ServerInfoClient(capturingClient(), "https://example.test").fetch()
        assertNull(captured.single().header("Authorization"))
    }
}
