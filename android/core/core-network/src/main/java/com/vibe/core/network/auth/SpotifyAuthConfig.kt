package com.vibe.core.network.auth

object SpotifyAuthConfig {
    // Developers can set their Spotify Client ID here or inject via buildConfig
    const val DEFAULT_CLIENT_ID = "vibe_spotify_client_id"
    const val REDIRECT_URI = "vibe://auth/callback"
    const val AUTHORIZATION_ENDPOINT = "https://accounts.spotify.com/authorize"
    const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

    val DEFAULT_SCOPES = listOf(
        "streaming",
        "user-read-playback-state",
        "user-modify-playback-state",
        "user-read-currently-playing",
        "user-read-email",
        "user-read-private",
        "playlist-read-private",
        "playlist-read-collaborative",
        "playlist-modify-public",
        "playlist-modify-private",
        "user-library-read",
        "user-library-modify",
        "user-top-read",
        "user-read-recently-played",
        "user-follow-read"
    )
}
