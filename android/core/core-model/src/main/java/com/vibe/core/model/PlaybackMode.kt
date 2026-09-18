package com.vibe.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlaybackMode {
    SPOTIFY_REMOTE, // Official Spotify App Remote: IPC direct playback (100% full tracks, 320 kbps Vorbis)
    STANDALONE,     // Spotube-style: Native ExoPlayer with multi-source resolver
    CONNECT         // Spotify Connect: Controls playback on external active Spotify devices
}

@Serializable
enum class AudioQuality {
    NORMAL, // 160 kbps
    HIGH    // 320 kbps
}

data class VibeSettings(
    val playbackMode: PlaybackMode = PlaybackMode.SPOTIFY_REMOTE,
    val autoFallbackEnabled: Boolean = true,
    val audioQuality: AudioQuality = AudioQuality.HIGH
)
