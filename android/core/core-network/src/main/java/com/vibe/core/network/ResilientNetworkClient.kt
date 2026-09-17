package com.vibe.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Custom OkHttpClient factory configured with a strict 5-second timeout per attempt
 * to prevent stalled connection hangs and trigger fast endpoint failover.
 */
object ResilientNetworkClient {
    private const val SPOTIFY_TIMEOUT_SECONDS = 5L

    fun createClient(
        authInterceptor: Interceptor? = null,
        authenticator: okhttp3.Authenticator? = null,
        loggingInterceptor: Interceptor? = null
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(SPOTIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(SPOTIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(SPOTIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        authInterceptor?.let { builder.addInterceptor(it) }
        authenticator?.let { builder.authenticator(it) }
        loggingInterceptor?.let { builder.addInterceptor(it) }

        return builder.build()
    }
}

class AuthTokenInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenProvider()
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
