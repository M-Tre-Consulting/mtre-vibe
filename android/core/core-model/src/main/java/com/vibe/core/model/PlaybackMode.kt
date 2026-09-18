package com.vibe.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlaybackMode {
    STANDALONE,   // Spotube-style: Native ExoPlayer with full-track audio resolver (Works Free & Premium)
    CONNECT,      // Spotify Connect: Controls playback on external active Spotify devices
    LIBRESPOT     // Integrated Librespot Connect Receiver (Experimental, requires Spotify Premium)
}

@Serializable
enum class AudioQuality {
    NORMAL, // 160 kbps
    HIGH    // 320 kbps
}

data class VibeSettings(
    val playbackMode: PlaybackMode = PlaybackMode.STANDALONE,
    val autoFallbackEnabled: Boolean = true,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val isLibrespotEnabled: Boolean = false
)
