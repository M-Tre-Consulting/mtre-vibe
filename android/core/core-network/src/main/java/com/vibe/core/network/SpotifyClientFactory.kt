package com.vibe.core.network

import com.vibe.core.network.api.SpotifyRetrofitApi
import com.vibe.core.network.auth.SpotifyAuthManager
import com.vibe.core.network.auth.SpotifyTokenAuthenticator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object SpotifyClientFactory {
    private const val SPOTIFY_BASE_URL = "https://api.spotify.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun createRetrofitApi(
        authManager: SpotifyAuthManager,
        enableLogging: Boolean = false
    ): SpotifyRetrofitApi {
        val authInterceptor = AuthTokenInterceptor {
            runBlocking { authManager.getValidAccessToken() }
        }
        val tokenAuthenticator = SpotifyTokenAuthenticator(authManager)

        val loggingInterceptor = if (enableLogging) {
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        } else null

        val okHttpClient: OkHttpClient = ResilientNetworkClient.createClient(
            authInterceptor = authInterceptor,
            authenticator = tokenAuthenticator,
            loggingInterceptor = loggingInterceptor
        )

        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(SPOTIFY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(SpotifyRetrofitApi::class.java)
    }

    fun createSpotifyApiService(
        authManager: SpotifyAuthManager,
        enableLogging: Boolean = false
    ): SpotifyApiService {
        val retrofitApi = createRetrofitApi(authManager, enableLogging)
        return SpotifyApiServiceImpl(retrofitApi)
    }
}
