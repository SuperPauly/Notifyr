package com.example.data.remote

import com.squareup.moshi.Moshi
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the authenticated [NotifyrApi] client.
 *
 * - The bearer token is supplied per request through [bearerProvider] (read from the Keystore at
 *   call time) and is deliberately never logged.
 * - Redirects are disabled so a misconfigured server cannot bounce a bearer token to another host.
 * - HTTP logging is added ONLY when [debugLogging] is true (callers pass `BuildConfig.DEBUG`), and
 *   only at BASIC level so the Authorization header is never captured.
 */
object ApiFactory {

    private const val TIMEOUT_SECONDS = 20L

    /**
     * @param baseClient optional pre-configured client. The bearer interceptor from
     *   [bearerProvider] is always applied on top, so the Authorization header is present
     *   regardless of the supplied client.
     */
    fun create(
        baseUrl: String,
        bearerProvider: () -> String?,
        moshi: Moshi = Moshi.Builder().build(),
        baseClient: OkHttpClient? = null
    ): NotifyrApi {
        val httpUrl: HttpUrl = baseUrl.toHttpUrl()
        val authed: Call.Factory = (baseClient ?: okHttpClient(bearerProvider = bearerProvider))
            .newBuilder()
            .addInterceptor(authInterceptor(bearerProvider))
            .build()
        return Retrofit.Builder()
            .baseUrl(httpUrl)
            .callFactory(authed)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NotifyrApi::class.java)
    }

    /**
     * Builds the transport client: timeouts, redirects disabled, optional BASIC logging, and the
     * bearer interceptor.
     */
    fun okHttpClient(
        bearerProvider: () -> String? = { null },
        debugLogging: Boolean = false
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor(bearerProvider))

        if (debugLogging) {
            // BASIC only: never BODY/HEADERS, which could capture the Authorization header.
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        return builder.build()
    }

    fun authInterceptor(bearerProvider: () -> String?): Interceptor = Interceptor { chain ->
        val token = bearerProvider()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        chain.proceed(request)
    }
}
