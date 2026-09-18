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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    val mediaSession: MediaSession
        get() = exoPlayer.mediaSession

    init {
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

    override fun playTrack(track: Track, contextTracks: List<Track>) {
        scope.launch {
            val settings = settingsManager.settingsFlow.first()
            Log.i(TAG, "playTrack called for '${track.name}' (Mode: ${settings.playbackMode}, AutoFallback: ${settings.autoFallbackEnabled})")

            when (settings.playbackMode) {
                PlaybackMode.CONNECT -> {
                    Log.i(TAG, "Routing track to Spotify Connect (Active Device: ${spotifyConnect.targetDeviceId})")
                    switchToConnect()
                    spotifyConnect.playTrack(track, contextTracks)
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
                        spotifyRemote.playTrack(track, contextTracks)
                    } else if (settings.autoFallbackEnabled) {
                        Log.i(TAG, "Spotify app not installed -> Falling back to ExoPlayer")
                        switchToExoPlayer()
                        exoPlayer.playTrack(track, contextTracks)
                    } else {
                        Log.w(TAG, "Spotify app not installed and autoFallback disabled")
                    }
                }
            }
        }
    }

    override fun playFilteredCollection(tracks: List<Track>, startIndex: Int) {
        scope.launch {
            val settings = settingsManager.settingsFlow.first()
            Log.i(TAG, "playFilteredCollection called with ${tracks.size} tracks (Mode: ${settings.playbackMode})")

            when (settings.playbackMode) {
                PlaybackMode.CONNECT -> {
                    switchToConnect()
                    spotifyConnect.playFilteredCollection(tracks, startIndex)
                }
                PlaybackMode.STANDALONE -> {
                    switchToExoPlayer()
                    exoPlayer.playFilteredCollection(tracks, startIndex)
                }
                PlaybackMode.SPOTIFY_REMOTE -> {
                    if (spotifyRemote.isSpotifyInstalled()) {
                        switchToSpotifyRemote()
                        spotifyRemote.playFilteredCollection(tracks, startIndex)
                    } else if (settings.autoFallbackEnabled) {
                        switchToExoPlayer()
                        exoPlayer.playFilteredCollection(tracks, startIndex)
                    }
                }
            }
        }
    }

    fun switchToConnect(deviceId: String? = null) {
        if (deviceId != null) {
            spotifyConnect.targetDeviceId = deviceId
        }
        if (activeEngine != ActiveEngine.CONNECT) {
            exoPlayer.pause()
            activeEngine = ActiveEngine.CONNECT
            spotifyConnect.startPolling()
            scope.launch {
                spotifyConnect.syncRemotePlaybackState()
            }
        }
    }

    fun switchToSpotifyRemote() {
        if (activeEngine != ActiveEngine.SPOTIFY_REMOTE) {
            if (activeEngine == ActiveEngine.CONNECT) {
                spotifyConnect.stopPolling()
                spotifyConnect.pause()
            }
            exoPlayer.pause()
            activeEngine = ActiveEngine.SPOTIFY_REMOTE
            _playbackState.value = spotifyRemote.playbackState.value
        }
    }

    fun switchToExoPlayer() {
        if (activeEngine != ActiveEngine.EXO_PLAYER) {
            if (activeEngine == ActiveEngine.CONNECT) {
                spotifyConnect.stopPolling()
                spotifyConnect.pause()
            }
            spotifyRemote.pause()
            activeEngine = ActiveEngine.EXO_PLAYER
            _playbackState.value = exoPlayer.playbackState.value
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

    override fun restoreSession(lastTrack: Track, positionMs: Long) {
        exoPlayer.restoreSession(lastTrack, positionMs)
        spotifyRemote.restoreSession(lastTrack, positionMs)
    }
}
