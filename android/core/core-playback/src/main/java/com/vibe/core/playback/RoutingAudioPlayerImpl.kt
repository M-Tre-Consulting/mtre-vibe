package com.vibe.core.playback

import android.content.Context
import android.util.Log
import androidx.media3.session.MediaSession
import com.vibe.core.model.PlaybackMode
import com.vibe.core.model.PlaybackState
import com.vibe.core.model.RepeatMode
import com.vibe.core.model.Track
import com.vibe.core.model.isCurrentDevice
import com.vibe.core.model.isRemote
import com.vibe.core.ui.R as UiR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Unified playback orchestrator that routes between:
 * 1. SPOTIFY_REMOTE: Official Spotify App Remote IPC on-device full-length playback
 * 2. CONNECT: Spotify Connect remote devices (PC, Mac, Linux, TV, smart speakers) via Web API
 * 3. STANDALONE: Native Media3 ExoPlayer internal engine
 */
class RoutingAudioPlayerImpl(
    private val context: Context,
    val exoPlayer: Media3AudioPlayerImpl,
    val spotifyRemote: SpotifyAppRemoteManager,
    val spotifyConnect: SpotifyConnectPlayerImpl,
    val queueManager: QueueManager,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : VibeAudioPlayer {

    companion object {
        private const val TAG = "VIBE_ROUTING"
    }

    enum class ActiveEngine {
        SPOTIFY_REMOTE,
        CONNECT,
        EXO_PLAYER
    }

    var activeEngine: ActiveEngine = ActiveEngine.SPOTIFY_REMOTE
        private set

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 20)
    override val errorEvents: Flow<String> = _errorEvents.asSharedFlow()

    override val queue: StateFlow<com.vibe.core.model.Queue> = queueManager.queue

    private var isPausedByUser = false

    val mediaSession: MediaSession
        get() = exoPlayer.mediaSession

    init {
        // Wire ExoPlayer MediaSession / completion callbacks to Vibe's unified queue
        exoPlayer.onSkipNextCallback = { skipToNext() }
        exoPlayer.onSkipPreviousCallback = { skipToPrevious() }
        exoPlayer.onTrackEndedCallback = { skipToNext() }

        // Forward error events from all sub-players
        scope.launch {
            spotifyRemote.errorEvents.collect { err ->
                _errorEvents.emit(cleanPlaybackError(err))
            }
        }
        scope.launch {
            spotifyConnect.errorEvents.collect { err ->
                _errorEvents.emit(cleanPlaybackError(err))
            }
        }
        scope.launch {
            exoPlayer.errorEvents.collect { err ->
                _errorEvents.emit(cleanPlaybackError(err))
            }
        }

        // Collect updates from Spotify App Remote
        scope.launch {
            var lastTrackId: String? = null
            var wasPlaying = false

            spotifyRemote.playbackState.collect { state ->
                if (activeEngine == ActiveEngine.SPOTIFY_REMOTE) {
                    _playbackState.value = state

                    val currentTrack = state.currentTrack
                    val isPlaying = state.isPlaying
                    val isPaused = state.isPaused
                    val pos = state.positionMs
                    val dur = state.durationMs

                    // Check if current track changed unexpectedly in Spotify
                    if (currentTrack != null && currentTrack.id != lastTrackId) {
                        lastTrackId = currentTrack.id

                        val activePlaying = queueManager.queue.value.currentlyPlaying
                        if (activePlaying != null && currentTrack.id != activePlaying.id) {
                            val expectedNext = queueManager.peekNext()
                            if (expectedNext != null && currentTrack.id == expectedNext.id) {
                                Log.i(TAG, "Spotify Remote transitioned naturally to '${currentTrack.name}'. Advancing Vibe queue.")
                                queueManager.advanceToNext()
                            } else if (expectedNext != null) {
                                Log.i(TAG, "Spotify Remote changed to unexpected track '${currentTrack.name}'. Overriding with Vibe queue track '${expectedNext.name}'")
                                skipToNext()
                            }
                        }
                    }

                    // Check if track ended naturally (playback stopped at/near end of track)
                    val nearEnd = dur > 0L && pos >= (dur - 2500L)
                    val stoppedAtEnd = wasPlaying && isPaused && !isPlaying && (nearEnd || (pos == 0L && dur > 0L))
                    if (stoppedAtEnd && !isPausedByUser) {
                        if (queueManager.hasUpcoming()) {
                            Log.i(TAG, "Track ended in Spotify Remote. Auto-advancing to next track in Vibe queue.")
                            skipToNext()
                        }
                    }

                    wasPlaying = isPlaying
                }
            }
        }

        // Collect updates from Spotify Connect
        scope.launch {
            spotifyConnect.playbackState.collect { state ->
                if (activeEngine == ActiveEngine.CONNECT) {
                    _playbackState.value = state
                }
            }
        }

        // Collect updates from ExoPlayer
        scope.launch {
            exoPlayer.playbackState.collect { state ->
                if (activeEngine == ActiveEngine.EXO_PLAYER) {
                    _playbackState.value = state
                }
            }
        }
    }

    private fun cleanPlaybackError(raw: String): String {
        if (raw.contains("Explicit user authorization is required", ignoreCase = true) ||
            raw.contains("UserNotAuthorizedException", ignoreCase = true)) {
            return context.getString(UiR.string.playback_error_ipc_unauthorized)
        }
        if (raw.contains("NO_ACTIVE_DEVICE", ignoreCase = true) || raw.contains("No active device", ignoreCase = true)) {
            return context.getString(UiR.string.playback_error_no_active_device)
        }
        if (raw.contains("Timeout", ignoreCase = true)) {
            return context.getString(UiR.string.playback_error_timeout)
        }
        val jsonRegex = Regex("""\{.*"message"\s*:\s*"([^"]+)".*\}""")
        val match = jsonRegex.find(raw)
        if (match != null) {
            return context.getString(UiR.string.playback_error_spotify_format, match.groupValues[1])
        }
        return raw
    }

    override fun playTrack(track: Track, contextTracks: List<Track>) {
        queueManager.setContextQueue(track, contextTracks)
        playTrackInternal(track)
    }

    private fun playTrackInternal(track: Track) {
        isPausedByUser = false
        scope.launch {
            val settings = settingsManager.settingsFlow.first()
            Log.i(TAG, "playTrackInternal called for '${track.name}' (ActiveEngine: $activeEngine, Mode: ${settings.playbackMode}, AutoFallback: ${settings.autoFallbackEnabled})")

            when (settings.playbackMode) {
                PlaybackMode.CONNECT -> {
                    Log.i(TAG, "Routing track to Spotify Connect (Active Device: ${spotifyConnect.targetDeviceId})")
                    switchToConnect()
                    val result = spotifyConnect.playTrackWithResult(track, emptyList())
                    if (result.isFailure) {
                        val rawErr = result.exceptionOrNull()?.message ?: context.getString(UiR.string.playback_error_connect_format, "Failed")
                        val errMsg = cleanPlaybackError(rawErr)
                        if (settings.autoFallbackEnabled) {
                            val msg = context.getString(UiR.string.playback_fallback_connect_local, errMsg)
                            Log.w(TAG, msg)
                            _errorEvents.emit(msg)
                            switchToExoPlayer()
                            exoPlayer.playTrack(track, emptyList())
                        } else {
                            _errorEvents.emit(context.getString(UiR.string.playback_error_connect_format, errMsg))
                        }
                    }
                }
                PlaybackMode.STANDALONE -> {
                    Log.i(TAG, "Routing track to ExoPlayer (STANDALONE mode)")
                    switchToExoPlayer()
                    exoPlayer.playTrack(track, emptyList())
                }
                PlaybackMode.SPOTIFY_REMOTE -> {
                    if (spotifyRemote.isSpotifyInstalled()) {
                        Log.i(TAG, "Routing track to Spotify App Remote IPC")
                        switchToSpotifyRemote()

                        // If a remote Connect device (e.g. PC, Speaker) is currently active and playing on the Spotify account,
                        // transfer active playback to this local device first so Spotify switches output locally.
                        try {
                            val activeDev = spotifyConnect.playbackState.value.activeDevice
                            if (activeDev != null && activeDev.isRemote) {
                                Log.i(TAG, "Remote device '${activeDev.name}' is currently playing on Spotify. Transferring session to local device for IPC...")
                                val devs = spotifyConnect.getAvailableDevices()
                                val localDev = devs.firstOrNull { it.isCurrentDevice(devs) }
                                if (localDev != null) {
                                    spotifyConnect.apiService.transferPlayback(localDev.id, play = false)
                                    delay(250)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Pre-transfer playback to local device notice: ${e.message}")
                        }

                        val result = spotifyRemote.playTrackWithResult(track, emptyList())
                        if (result.isFailure) {
                            val rawErr = result.exceptionOrNull()?.message ?: "Spotify App"
                            val errMsg = cleanPlaybackError(rawErr)
                            Log.w(TAG, "Spotify App Remote failed: $errMsg")

                            if (settings.autoFallbackEnabled) {
                                // Fallback directly to ExoPlayer local engine on this device!
                                // Never fallback to a remote PC/speaker when user chose SPOTIFY_REMOTE local playback!
                                val fallbackMsg = context.getString(UiR.string.playback_fallback_local, errMsg)
                                Log.w(TAG, fallbackMsg)
                                _errorEvents.emit(fallbackMsg)
                                switchToExoPlayer()
                                exoPlayer.playTrack(track, emptyList())
                            } else {
                                _errorEvents.emit("Spotify App Remote: $errMsg")
                            }
                        }
                    } else if (settings.autoFallbackEnabled) {
                        // Spotify app not installed -> fallback to ExoPlayer
                        val msg = context.getString(UiR.string.playback_fallback_not_installed)
                        Log.i(TAG, msg)
                        _errorEvents.emit(msg)
                        switchToExoPlayer()
                        exoPlayer.playTrack(track, emptyList())
                    } else {
                        val msg = context.getString(UiR.string.spotify_not_installed)
                        Log.w(TAG, msg)
                        _errorEvents.emit(msg)
                    }
                }
            }
        }
    }

    override fun playFilteredCollection(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val targetTrack = tracks.getOrNull(startIndex) ?: tracks.first()
        playTrack(targetTrack, tracks)
    }

    fun switchToConnect(deviceId: String? = null, transferPlayback: Boolean = false) {
        val prevTrack = _playbackState.value.currentTrack
        val wasPlaying = _playbackState.value.isPlaying

        if (deviceId != null) {
            spotifyConnect.targetDeviceId = deviceId
        }
        if (activeEngine != ActiveEngine.CONNECT) {
            spotifyRemote.pause()
            exoPlayer.pause()
            activeEngine = ActiveEngine.CONNECT
            spotifyConnect.startPolling()

            if (transferPlayback && prevTrack != null && wasPlaying) {
                spotifyConnect.playTrack(prevTrack, emptyList())
            } else {
                scope.launch {
                    spotifyConnect.syncRemotePlaybackState()
                }
            }
        } else {
            spotifyConnect.startPolling()
            scope.launch {
                spotifyConnect.syncRemotePlaybackState()
            }
        }
    }

    fun switchToSpotifyRemote(transferPlayback: Boolean = false) {
        val prevTrack = _playbackState.value.currentTrack
        val wasPlaying = _playbackState.value.isPlaying

        if (activeEngine != ActiveEngine.SPOTIFY_REMOTE) {
            if (activeEngine == ActiveEngine.CONNECT) {
                // Only stop polling. DO NOT call spotifyConnect.pause(),
                // because sending a Web API pause command to the Spotify account
                // will pause the local Spotify App Remote playback right as it begins!
                spotifyConnect.stopPolling()
            }
            exoPlayer.pause()
            activeEngine = ActiveEngine.SPOTIFY_REMOTE

            scope.launch {
                if (spotifyRemote.isSpotifyInstalled() && !spotifyRemote.isConnectedFlow.value) {
                    spotifyRemote.connect(showAuthView = false)
                }
            }

            if (transferPlayback && prevTrack != null && wasPlaying) {
                spotifyRemote.playTrack(prevTrack)
            } else {
                _playbackState.update { current ->
                    val remoteState = spotifyRemote.playbackState.value
                    if (remoteState.currentTrack != null) remoteState
                    else remoteState.copy(currentTrack = prevTrack ?: current.currentTrack)
                }
            }
        }
    }

    fun switchToExoPlayer(transferPlayback: Boolean = false) {
        val prevTrack = _playbackState.value.currentTrack
        val wasPlaying = _playbackState.value.isPlaying

        if (activeEngine != ActiveEngine.EXO_PLAYER) {
            if (activeEngine == ActiveEngine.CONNECT) {
                spotifyConnect.pause()
                spotifyConnect.stopPolling()
            }
            spotifyRemote.pause()
            activeEngine = ActiveEngine.EXO_PLAYER

            if (transferPlayback && prevTrack != null && wasPlaying) {
                exoPlayer.playTrack(prevTrack)
            } else {
                _playbackState.update { current ->
                    val exoState = exoPlayer.playbackState.value
                    if (exoState.currentTrack != null) exoState
                    else exoState.copy(currentTrack = prevTrack ?: current.currentTrack)
                }
            }
        }
    }

    override fun pause() {
        isPausedByUser = true
        Log.d(TAG, "Forwarding pause to $activeEngine")
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.pause()
            ActiveEngine.CONNECT -> spotifyConnect.pause()
            ActiveEngine.EXO_PLAYER -> exoPlayer.pause()
        }
    }

    override fun resume() {
        isPausedByUser = false
        Log.d(TAG, "Forwarding resume to $activeEngine")
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.resume()
            ActiveEngine.CONNECT -> spotifyConnect.resume()
            ActiveEngine.EXO_PLAYER -> exoPlayer.resume()
        }
    }

    override fun stop() {
        isPausedByUser = true
        Log.d(TAG, "Forwarding stop to all engines")
        spotifyRemote.stop()
        spotifyConnect.stop()
        exoPlayer.stop()
    }

    override fun seekTo(positionMs: Long) {
        Log.d(TAG, "Forwarding seekTo($positionMs) to $activeEngine")
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.seekTo(positionMs)
            ActiveEngine.CONNECT -> spotifyConnect.seekTo(positionMs)
            ActiveEngine.EXO_PLAYER -> exoPlayer.seekTo(positionMs)
        }
    }

    override fun skipToNext() {
        val nextTrack = queueManager.advanceToNext()
        if (nextTrack != null) {
            Log.i(TAG, "skipToNext: Advancing to next track in Vibe queue: '${nextTrack.name}'")
            playTrackInternal(nextTrack)
        } else {
            Log.i(TAG, "skipToNext: Queue is empty, delegating to activeEngine ($activeEngine)")
            when (activeEngine) {
                ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.skipToNext()
                ActiveEngine.CONNECT -> spotifyConnect.skipToNext()
                ActiveEngine.EXO_PLAYER -> exoPlayer.skipToNext()
            }
        }
    }

    override fun skipToPrevious() {
        if (_playbackState.value.positionMs > 3000L) {
            Log.d(TAG, "skipToPrevious: position > 3s, rewinding to beginning")
            seekTo(0L)
            return
        }
        val prevTrack = queueManager.advanceToPrevious()
        if (prevTrack != null) {
            Log.i(TAG, "skipToPrevious: Restoring previous track: '${prevTrack.name}'")
            playTrackInternal(prevTrack)
        } else {
            Log.i(TAG, "skipToPrevious: History is empty, delegating to activeEngine ($activeEngine)")
            when (activeEngine) {
                ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.skipToPrevious()
                ActiveEngine.CONNECT -> spotifyConnect.skipToPrevious()
                ActiveEngine.EXO_PLAYER -> exoPlayer.skipToPrevious()
            }
        }
    }

    override fun setShuffle(enabled: Boolean) {
        Log.d(TAG, "Forwarding setShuffle($enabled) to $activeEngine")
        queueManager.setShuffle(enabled)
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.setShuffle(enabled)
            ActiveEngine.CONNECT -> spotifyConnect.setShuffle(enabled)
            ActiveEngine.EXO_PLAYER -> exoPlayer.setShuffle(enabled)
        }
    }

    override fun toggleShuffle() {
        val next = !_playbackState.value.shuffleEnabled
        setShuffle(next)
    }

    override fun setSmartShuffle(enabled: Boolean) {
        exoPlayer.setSmartShuffle(enabled)
    }

    override fun toggleSmartShuffle() {
        exoPlayer.toggleSmartShuffle()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        Log.d(TAG, "Forwarding setRepeatMode($mode) to $activeEngine")
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.setRepeatMode(mode)
            ActiveEngine.CONNECT -> spotifyConnect.setRepeatMode(mode)
            ActiveEngine.EXO_PLAYER -> exoPlayer.setRepeatMode(mode)
        }
    }

    override fun toggleRepeat() {
        val next = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(next)
    }

    override fun setVolume(volume: Float) {
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.setVolume(volume)
            ActiveEngine.CONNECT -> spotifyConnect.setVolume(volume)
            ActiveEngine.EXO_PLAYER -> exoPlayer.setVolume(volume)
        }
    }

    override fun addToQueue(track: Track) {
        Log.i(TAG, "Adding track to Vibe Queue: '${track.name}'")
        queueManager.addToUserQueue(track)
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int, isUserQueue: Boolean) {
        Log.i(TAG, "moveQueueItem($fromIndex, $toIndex, isUserQueue=$isUserQueue)")
        if (isUserQueue) {
            queueManager.moveUserQueueItem(fromIndex, toIndex)
        } else {
            queueManager.moveContextQueueItem(fromIndex, toIndex)
        }
        if (activeEngine == ActiveEngine.EXO_PLAYER) {
            exoPlayer.moveQueueItem(fromIndex, toIndex)
        }
    }

    override fun removeQueueItem(track: Track) {
        Log.i(TAG, "removeQueueItem('${track.name}')")
        queueManager.removeTrack(track)
    }

    override fun clearQueue() {
        Log.i(TAG, "clearQueue()")
        queueManager.clearQueue()
    }

    override fun playQueueItem(track: Track) {
        Log.i(TAG, "playQueueItem('${track.name}')")
        queueManager.playTrackFromQueue(track)
        playTrackInternal(track)
    }

    override fun restoreSession(lastTrack: Track, positionMs: Long) {
        queueManager.setContextQueue(lastTrack, listOf(lastTrack))
        exoPlayer.restoreSession(lastTrack, positionMs)
        spotifyRemote.restoreSession(lastTrack, positionMs)
    }
}
