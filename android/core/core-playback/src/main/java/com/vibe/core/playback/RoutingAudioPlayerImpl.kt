package com.vibe.core.playback

import android.content.Context
import android.util.Log
import androidx.media3.session.MediaSession
import com.vibe.core.model.PlaybackMode
import com.vibe.core.model.PlaybackState
import com.vibe.core.model.RepeatMode
import com.vibe.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private var activeEngine: ActiveEngine = ActiveEngine.SPOTIFY_REMOTE

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 20)
    override val errorEvents: Flow<String> = _errorEvents.asSharedFlow()

    val mediaSession: MediaSession
        get() = exoPlayer.mediaSession

    init {
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
            spotifyRemote.playbackState.collect { state ->
                if (activeEngine == ActiveEngine.SPOTIFY_REMOTE) {
                    _playbackState.value = state
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
            return "IPC Spotify non autorizzato su questo dispositivo. Seleziona un dispositivo o apri Spotify per attivare Connect."
        }
        if (raw.contains("NO_ACTIVE_DEVICE", ignoreCase = true) || raw.contains("No active device", ignoreCase = true)) {
            return "Nessun dispositivo attivo: apri Spotify sul telefono o PC, oppure selezionalo da Dispositivi."
        }
        if (raw.contains("Timeout", ignoreCase = true)) {
            return "Timeout risposta da Spotify: verifica che l'app Spotify o il dispositivo siano attivi."
        }
        val jsonRegex = Regex("""\{.*"message"\s*:\s*"([^"]+)".*\}""")
        val match = jsonRegex.find(raw)
        if (match != null) {
            return "Errore Spotify: ${match.groupValues[1]}"
        }
        return raw
    }

    override fun playTrack(track: Track, contextTracks: List<Track>) {
        scope.launch {
            val settings = settingsManager.settingsFlow.first()
            Log.i(TAG, "playTrack called for '${track.name}' (Mode: ${settings.playbackMode}, AutoFallback: ${settings.autoFallbackEnabled})")

            when (settings.playbackMode) {
                PlaybackMode.CONNECT -> {
                    Log.i(TAG, "Routing track to Spotify Connect (Active Device: ${spotifyConnect.targetDeviceId})")
                    switchToConnect()
                    val result = spotifyConnect.playTrackWithResult(track, contextTracks)
                    if (result.isFailure) {
                        val errMsg = cleanPlaybackError(result.exceptionOrNull()?.message ?: "Errore Spotify Connect")
                        if (settings.autoFallbackEnabled) {
                            val msg = "Spotify Connect non risponde ($errMsg). Avvio riproduzione locale..."
                            Log.w(TAG, msg)
                            _errorEvents.emit(msg)
                            switchToExoPlayer()
                            exoPlayer.playTrack(track, contextTracks)
                        } else {
                            _errorEvents.emit("Spotify Connect: $errMsg")
                        }
                    }
                }
                PlaybackMode.STANDALONE -> {
                    Log.i(TAG, "Routing track to ExoPlayer (STANDALONE mode)")
                    switchToExoPlayer()
                    exoPlayer.playTrack(track, contextTracks)
                }
                PlaybackMode.SPOTIFY_REMOTE -> {
                    if (spotifyRemote.isSpotifyInstalled()) {
                        Log.i(TAG, "Routing track to Spotify App Remote IPC")
                        switchToSpotifyRemote()
                        val result = spotifyRemote.playTrackWithResult(track, contextTracks)
                        if (result.isFailure) {
                            val rawErr = result.exceptionOrNull()?.message ?: "Spotify App non risponde"
                            val errMsg = cleanPlaybackError(rawErr)
                            Log.w(TAG, "Spotify App Remote failed: $errMsg")

                            if (settings.autoFallbackEnabled) {
                                // 1. Attempt fallback to Spotify Connect (either local phone or remote PC)
                                Log.i(TAG, "Attempting fallback to Spotify Connect...")
                                switchToConnect()
                                val connectResult = spotifyConnect.playTrackWithResult(track, contextTracks)
                                if (connectResult.isSuccess) {
                                    val targetName = spotifyConnect.playbackState.value.activeDevice?.name ?: "Spotify Connect"
                                    val fallbackMsg = "Riproduzione avviata su $targetName via Spotify Connect."
                                    Log.i(TAG, fallbackMsg)
                                    _errorEvents.emit(fallbackMsg)
                                } else {
                                    // 2. If Connect also fails, fallback to ExoPlayer local engine
                                    val fallbackMsg = "Spotify non disponibile ($errMsg). Avvio riproduzione locale..."
                                    Log.w(TAG, fallbackMsg)
                                    _errorEvents.emit(fallbackMsg)
                                    switchToExoPlayer()
                                    exoPlayer.playTrack(track, contextTracks)
                                }
                            } else {
                                _errorEvents.emit("Spotify App Remote: $errMsg")
                            }
                        }
                    } else if (settings.autoFallbackEnabled) {
                        // Spotify app not installed -> try Connect first, then ExoPlayer
                        switchToConnect()
                        val connectResult = spotifyConnect.playTrackWithResult(track, contextTracks)
                        if (connectResult.isSuccess) {
                            val targetName = spotifyConnect.playbackState.value.activeDevice?.name ?: "PC"
                            _errorEvents.emit("Riproduzione avviata su $targetName via Spotify Connect.")
                        } else {
                            val msg = "App Spotify non installata. Avvio riproduzione locale..."
                            Log.i(TAG, msg)
                            _errorEvents.emit(msg)
                            switchToExoPlayer()
                            exoPlayer.playTrack(track, contextTracks)
                        }
                    } else {
                        val msg = "App Spotify non installata su questo dispositivo"
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
                spotifyConnect.pause()
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
        Log.d(TAG, "Forwarding pause to $activeEngine")
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.pause()
            ActiveEngine.CONNECT -> spotifyConnect.pause()
            ActiveEngine.EXO_PLAYER -> exoPlayer.pause()
        }
    }

    override fun resume() {
        Log.d(TAG, "Forwarding resume to $activeEngine")
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.resume()
            ActiveEngine.CONNECT -> spotifyConnect.resume()
            ActiveEngine.EXO_PLAYER -> exoPlayer.resume()
        }
    }

    override fun stop() {
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
        Log.d(TAG, "Forwarding skipToNext to $activeEngine")
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.skipToNext()
            ActiveEngine.CONNECT -> spotifyConnect.skipToNext()
            ActiveEngine.EXO_PLAYER -> exoPlayer.skipToNext()
        }
    }

    override fun skipToPrevious() {
        Log.d(TAG, "Forwarding skipToPrevious to $activeEngine")
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.skipToPrevious()
            ActiveEngine.CONNECT -> spotifyConnect.skipToPrevious()
            ActiveEngine.EXO_PLAYER -> exoPlayer.skipToPrevious()
        }
    }

    override fun setShuffle(enabled: Boolean) {
        Log.d(TAG, "Forwarding setShuffle($enabled) to $activeEngine")
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.setShuffle(enabled)
            ActiveEngine.CONNECT -> spotifyConnect.setShuffle(enabled)
            ActiveEngine.EXO_PLAYER -> exoPlayer.setShuffle(enabled)
        }
    }

    override fun toggleShuffle() {
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.toggleShuffle()
            ActiveEngine.CONNECT -> spotifyConnect.toggleShuffle()
            ActiveEngine.EXO_PLAYER -> exoPlayer.toggleShuffle()
        }
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
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.toggleRepeat()
            ActiveEngine.CONNECT -> spotifyConnect.toggleRepeat()
            ActiveEngine.EXO_PLAYER -> exoPlayer.toggleRepeat()
        }
    }

    override fun setVolume(volume: Float) {
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.setVolume(volume)
            ActiveEngine.CONNECT -> spotifyConnect.setVolume(volume)
            ActiveEngine.EXO_PLAYER -> exoPlayer.setVolume(volume)
        }
    }

    override fun addToQueue(track: Track) {
        Log.i(TAG, "Forwarding addToQueue('${track.name}') to $activeEngine")
        when (activeEngine) {
            ActiveEngine.SPOTIFY_REMOTE -> spotifyRemote.addToQueue(track)
            ActiveEngine.CONNECT -> spotifyConnect.addToQueue(track)
            ActiveEngine.EXO_PLAYER -> exoPlayer.addToQueue(track)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        exoPlayer.moveQueueItem(fromIndex, toIndex)
    }

    override fun restoreSession(lastTrack: Track, positionMs: Long) {
        exoPlayer.restoreSession(lastTrack, positionMs)
        spotifyRemote.restoreSession(lastTrack, positionMs)
    }
}
