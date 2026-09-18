package com.vibe.core.playback

import com.vibe.core.model.PlaybackState
import com.vibe.core.model.RepeatMode
import com.vibe.core.model.Track
import kotlinx.coroutines.flow.StateFlow

interface VibeAudioPlayer {
    val playbackState: StateFlow<PlaybackState>

    /**
     * Prepares and starts playback of a track with instant metadata display
     * while audio stream connects.
     */
    fun playTrack(track: Track, contextTracks: List<Track> = emptyList())

    /**
     * Plays tracks restricted strictly to the current active filtered subset.
     * Preserves repeated tracks.
     */
    fun playFilteredCollection(
        tracks: List<Track>,
        startIndex: Int = 0
    )

    fun pause()
    fun resume()
    fun stop()

    /**
     * Seeks to target position, immediately discarding audio queued from the old position.
     */
    fun seekTo(positionMs: Long)

    fun skipToNext()
    fun skipToPrevious()

    fun setShuffle(enabled: Boolean)
    fun toggleShuffle()
    fun setSmartShuffle(enabled: Boolean)
    fun toggleSmartShuffle()
    fun setRepeatMode(mode: RepeatMode)
    fun toggleRepeat()
    fun setVolume(volume: Float)

    /**
     * Appends a track to the active player queue.
     */
    fun addToQueue(track: Track)

    /**
     * Restores the last paused session on startup without auto-playing.
     */
    fun restoreSession(lastTrack: Track, positionMs: Long)
}
