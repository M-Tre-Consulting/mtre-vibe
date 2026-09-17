package com.vibe.core.network.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "vibe_auth_prefs")

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String = "Bearer",
    val scope: String? = null,
    val expires_in: Long,
    val refresh_token: String? = null
)

sealed interface AuthState {
    data object Loading : AuthState
    data class Authenticated(val accessToken: String, val expiresAt: Long) : AuthState
    data object Unauthenticated : AuthState
}

class SpotifyAuthManager(
    private val context: Context,
    private val clientId: String = SpotifyAuthConfig.DEFAULT_CLIENT_ID,
    private val redirectUri: String = SpotifyAuthConfig.REDIRECT_URI,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_EXPIRES_AT = longPreferencesKey("expires_at")
        private val KEY_CODE_VERIFIER = stringPreferencesKey("code_verifier")
    }

    val authState: Flow<AuthState> = context.authDataStore.data.map { prefs ->
        val token = prefs[KEY_ACCESS_TOKEN]
        val expiresAt = prefs[KEY_EXPIRES_AT] ?: 0L
        if (!token.isNullOrBlank() && expiresAt > System.currentTimeMillis()) {
            AuthState.Authenticated(token, expiresAt)
        } else if (!prefs[KEY_REFRESH_TOKEN].isNullOrBlank()) {
            // Has refresh token, can be refreshed on demand
            AuthState.Loading
        } else {
            AuthState.Unauthenticated
        }
    }

    suspend fun launchLogin(context: Context) {
        val codeVerifier = PkceCrypto.generateCodeVerifier()
        val codeChallenge = PkceCrypto.generateCodeChallenge(codeVerifier)

        // Persist code verifier to match callback
        context.authDataStore.edit { prefs ->
            prefs[KEY_CODE_VERIFIER] = codeVerifier
        }

        val scopesString = SpotifyAuthConfig.DEFAULT_SCOPES.joinToString(" ")
        val authUri = Uri.parse(SpotifyAuthConfig.AUTHORIZATION_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", scopesString)
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        customTabsIntent.launchUrl(context, authUri)
    }

    suspend fun handleAuthCallback(uri: Uri): Result<Unit> = withContext(ioDispatcher) {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            return@withContext Result.failure(IOException("Spotify authentication error: $error"))
        }

        val code = uri.getQueryParameter("code")
            ?: return@withContext Result.failure(IOException("Missing authorization code in callback"))

        val prefs = context.authDataStore.data.first()
        val codeVerifier = prefs[KEY_CODE_VERIFIER]
            ?: return@withContext Result.failure(IOException("Missing PKCE code verifier"))

        exchangeCodeForTokens(code, codeVerifier)
    }

    private suspend fun exchangeCodeForTokens(code: String, codeVerifier: String): Result<Unit> = withContext(ioDispatcher) {
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url(SpotifyAuthConfig.TOKEN_ENDPOINT)
            .post(formBody)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        runCatching {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string()
                throw IOException("Token exchange failed HTTP ${response.code}: $errBody")
            }

            val bodyString = response.body?.string() ?: throw IOException("Empty token response")
            val tokenResponse = json.decodeFromString<TokenResponse>(bodyString)

            val expiresAt = System.currentTimeMillis() + (tokenResponse.expires_in * 1000L)
            saveTokens(tokenResponse.access_token, tokenResponse.refresh_token, expiresAt)
        }
    }

    suspend fun getValidAccessToken(): String? = withContext(ioDispatcher) {
        val prefs = context.authDataStore.data.first()
        val accessToken = prefs[KEY_ACCESS_TOKEN]
        val refreshToken = prefs[KEY_REFRESH_TOKEN]
        val expiresAt = prefs[KEY_EXPIRES_AT] ?: 0L

        // Buffer: refresh if within 60 seconds of expiration
        val isExpiredOrExpiring = System.currentTimeMillis() + 60_000L >= expiresAt

        if (!accessToken.isNullOrBlank() && !isExpiredOrExpiring) {
            return@withContext accessToken
        }

        if (!refreshToken.isNullOrBlank()) {
            return@withContext refreshAccessToken(refreshToken)
        }

        null
    }

    suspend fun refreshAccessToken(refreshToken: String? = null): String? = refreshMutex.withLock {
        withContext(ioDispatcher) {
            val tokenToUse = refreshToken ?: context.authDataStore.data.first()[KEY_REFRESH_TOKEN]
            if (tokenToUse.isNullOrBlank()) return@withContext null

            val formBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", tokenToUse)
                .build()

            val request = Request.Builder()
                .url(SpotifyAuthConfig.TOKEN_ENDPOINT)
                .post(formBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val bodyString = response.body?.string() ?: return@withContext null
                val tokenResponse = json.decodeFromString<TokenResponse>(bodyString)

                val expiresAt = System.currentTimeMillis() + (tokenResponse.expires_in * 1000L)
                val newRefreshToken = tokenResponse.refresh_token ?: tokenToUse
                saveTokens(tokenResponse.access_token, newRefreshToken, expiresAt)
                tokenResponse.access_token
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun logout() = withContext(ioDispatcher) {
        context.authDataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_EXPIRES_AT)
            prefs.remove(KEY_CODE_VERIFIER)
        }
    }

    private suspend fun saveTokens(accessToken: String, refreshToken: String?, expiresAt: Long) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) {
                prefs[KEY_REFRESH_TOKEN] = refreshToken
            }
            prefs[KEY_EXPIRES_AT] = expiresAt
            prefs.remove(KEY_CODE_VERIFIER)
        }
    }
}
