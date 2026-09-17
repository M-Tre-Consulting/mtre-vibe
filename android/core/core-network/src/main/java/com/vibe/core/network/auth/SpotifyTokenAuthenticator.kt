package com.vibe.core.network.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Transparently catches HTTP 401 Unauthorized from Spotify Web API,
 * calls refresh_token, and retries the request with the fresh token.
 */
class SpotifyTokenAuthenticator(
    private val authManager: SpotifyAuthManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite retry loop if refresh fails repeatedly
        if (responseCount(response) >= 3) {
            return null
        }

        val newToken = runBlocking {
            authManager.refreshAccessToken()
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
