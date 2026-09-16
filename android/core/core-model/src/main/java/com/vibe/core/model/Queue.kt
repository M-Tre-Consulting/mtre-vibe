package com.vibe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Queue(
    val currentlyPlaying: Track? = null,
    val contextName: String? = null,
    val userQueue: List<Track> = emptyList(),
    val contextQueue: List<Track> = emptyList(),
    val recentHistory: List<PlayedTrackRecord> = emptyList()
)

@Serializable
data class PlayedTrackRecord(
    val track: Track,
    val playedAtTimestamp: Long = System.currentTimeMillis(),
    val durationPlayedMs: Long = 0L,
    val isShortSongRepeat: Boolean = false
)
