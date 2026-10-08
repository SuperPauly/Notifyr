package com.example.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Tiny fake HTTP layer: no MockWebServer dependency exists, so canned responses
 * are produced by an OkHttp interceptor and failures by throwing from it.
 */
object FakeHttp {
    fun clientReturning(code: Int, body: String, contentType: String = "application/json"): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain -> respond(chain, code, body, contentType) }
            .build()

    fun failingClient(message: String = "connect timed out"): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { throw IOException(message) }
            .build()

    fun respond(
        chain: Interceptor.Chain,
        code: Int,
        body: String,
        contentType: String = "application/json"
    ): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("fake")
        .body(body.toResponseBody(contentType.toMediaType()))
        .build()

    fun p256Key(seed: Byte = 0x11, first: Byte = 0x04, size: Int = 65): ByteArray =
        ByteArray(size) { if (it == 0) first else seed }

    fun base64Url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
