package com.vibe.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

@Serializable
data class PlaybackState(
    val currentTrack: Track? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isPaused: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1.0f,
    val activeDevice: Device? = null,
    val contextUri: String? = null,
    val contextTitle: String? = null,
    val audioBitrateKbps: Int = 320,
    val isGaplessActive: Boolean = true,
    val isVolumeNormalized: Boolean = true
)
