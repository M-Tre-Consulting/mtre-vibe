package com.vibe.core.playback

import android.util.Log
import com.vibe.core.model.PlaybackState
import com.vibe.core.model.RepeatMode
import com.vibe.core.model.Track
import com.vibe.core.model.isRemote
import com.vibe.core.network.SpotifyApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Controller for active Spotify Connect remote devices (PC, Mac, Linux, TV, smart speaker).
 * Controls remote playback directly via Spotify Web API Connect endpoints while synchronizing
 * full player state (current track, duration, position, volume) bidirectionally.
 */
class SpotifyConnectPlayerImpl(
    private val apiService: SpotifyApiService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : VibeAudioPlayer {

    companion object {
        private const val TAG = "VIBE_CONNECT"
    }

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    override val errorEvents: Flow<String> = _errorEvents.asSharedFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var pollJob: Job? = null
    var targetDeviceId: String? = null

    fun startPolling() {
        if (pollJob?.isActive == true) return
        Log.i(TAG, "Starting Spotify Connect state polling loop (targetDeviceId=$targetDeviceId)...")
        pollJob = scope.launch {
            while (isActive) {
                syncRemotePlaybackState()
                delay(1500)
            }
        }
    }

    fun stopPolling() {
        Log.i(TAG, "Stopping Spotify Connect state polling loop.")
        pollJob?.cancel()
        pollJob = null
    }

    suspend fun syncRemotePlaybackState() {
        val result = apiService.getPlaybackState()
        result.onSuccess { state ->
            if (state != null) {
                val current = _playbackState.value
                val resolvedTrack = state.currentTrack ?: current.currentTrack
                val merged = state.copy(currentTrack = resolvedTrack)
                _playbackState.value = merged
                val activeDev = state.activeDevice
                if (activeDev != null) {
                    targetDeviceId = activeDev.id
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed to poll Spotify Connect playback state: ${err.message}")
        }
    }

    suspend fun playTrackWithResult(track: Track, contextTracks: List<Track>): Result<Unit> {
        startPolling()
        val trackUri = if (track.uri.startsWith("spotify:track:")) track.uri else "spotify:track:${track.id}"
        val uris = if (contextTracks.isNotEmpty()) {
            val index = contextTracks.indexOfFirst { it.id == track.id }
            if (index != -1) {
                contextTracks.drop(index).take(50).map {
                    if (it.uri.startsWith("spotify:track:")) it.uri else "spotify:track:${it.id}"
                }
            } else listOf(trackUri)
        } else {
            listOf(trackUri)
        }
        Log.i(TAG, "Connect playTrack: '${track.name}' (${uris.size} uris) -> Target Device: $targetDeviceId")

        // Ensure target device is known
        var targetDev = targetDeviceId
        if (targetDev == null) {
            val devicesResult = apiService.getAvailableDevices()
            val devices = devicesResult.getOrNull() ?: emptyList()
            val activeOrFirst = devices.firstOrNull { it.isActive }
                ?: devices.firstOrNull { it.isRemote }
                ?: devices.firstOrNull()
            if (activeOrFirst != null) {
                targetDev = activeOrFirst.id
                targetDeviceId = targetDev
            }
        }

        var res = apiService.startPlayback(uris = uris, deviceId = targetDev)
        if (res.isFailure && targetDev != null) {
            Log.w(TAG, "Initial startPlayback failed, attempting transferPlayback to '$targetDev'...")
            apiService.transferPlayback(targetDev, play = true)
            delay(350)
            res = apiService.startPlayback(uris = listOf(trackUri), deviceId = targetDev)
        }
        if (res.isFailure) {
            // Fallback: try starting playback without explicit deviceId (controls currently active device)
            res = apiService.startPlayback(uris = uris, deviceId = null)
        }

        return if (res.isSuccess) {
            _playbackState.update {
                it.copy(
                    currentTrack = track,
                    isPlaying = true,
                    isPaused = false,
                    positionMs = 0L,
                    durationMs = track.durationMs,
                    lastErrorMessage = null
                )
            }
            delay(600)
            syncRemotePlaybackState()
            Result.success(Unit)
        } else {
            val errMsg = res.exceptionOrNull()?.message ?: "Impossibile avviare riproduzione su Spotify Connect"
            Log.e(TAG, "Failed to start Connect playback on device '$targetDev': $errMsg")
            _playbackState.update { it.copy(lastErrorMessage = errMsg) }
            _errorEvents.emit("Errore Spotify Connect: $errMsg")
            Result.failure(Exception(errMsg))
        }
    }

    override fun playTrack(track: Track, contextTracks: List<Track>) {
        scope.launch {
            playTrackWithResult(track, contextTracks)
        }
    }

    override fun playFilteredCollection(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val targetTrack = tracks.getOrNull(startIndex) ?: tracks.first()
        playTrack(targetTrack, tracks)
    }

    override fun pause() {
        scope.launch {
            Log.d(TAG, "Connect pausePlayback on device: $targetDeviceId")
            _playbackState.update { it.copy(isPlaying = false, isPaused = true) }
            val res = apiService.pausePlayback(deviceId = targetDeviceId)
            if (res.isFailure && targetDeviceId != null) {
                apiService.pausePlayback(deviceId = null)
            }
            delay(200)
            syncRemotePlaybackState()
        }
    }

    override fun resume() {
        startPolling()
        scope.launch {
            var targetDev = targetDeviceId
            if (targetDev == null) {
                val devicesResult = apiService.getAvailableDevices()
                val devices = devicesResult.getOrNull() ?: emptyList()
                val activeOrFirst = devices.firstOrNull { it.isActive }
                    ?: devices.firstOrNull { it.isRemote }
                    ?: devices.firstOrNull()
                if (activeOrFirst != null) {
                    targetDev = activeOrFirst.id
                    targetDeviceId = targetDev
                }
            }
            Log.d(TAG, "Connect resumePlayback on device: $targetDev")
            _playbackState.update { it.copy(isPlaying = true, isPaused = false) }
            val res = apiService.resumePlayback(deviceId = targetDev)
            if (res.isFailure && targetDev != null) {
                apiService.resumePlayback(deviceId = null)
            }
            delay(200)
            syncRemotePlaybackState()
        }
    }

    override fun stop() {
        pause()
    }

    override fun seekTo(positionMs: Long) {
        scope.launch {
            Log.d(TAG, "Connect seekTo($positionMs) on device: $targetDeviceId")
            _playbackState.update { it.copy(positionMs = positionMs) }
            val res = apiService.seekToPosition(positionMs, deviceId = targetDeviceId)
            if (res.isFailure && targetDeviceId != null) {
                apiService.seekToPosition(positionMs, deviceId = null)
            }
            delay(200)
            syncRemotePlaybackState()
        }
    }

    override fun skipToNext() {
        scope.launch {
            Log.d(TAG, "Connect skipToNext on device: $targetDeviceId")
            val res = apiService.skipToNext(deviceId = targetDeviceId)
            if (res.isFailure && targetDeviceId != null) {
                apiService.skipToNext(deviceId = null)
            }
            delay(300)
            syncRemotePlaybackState()
        }
    }

    override fun skipToPrevious() {
        scope.launch {
            Log.d(TAG, "Connect skipToPrevious on device: $targetDeviceId")
            val res = apiService.skipToPrevious(deviceId = targetDeviceId)
            if (res.isFailure && targetDeviceId != null) {
                apiService.skipToPrevious(deviceId = null)
            }
            delay(300)
            syncRemotePlaybackState()
        }
    }

    override fun setShuffle(enabled: Boolean) {
        scope.launch {
            Log.d(TAG, "Connect setShuffle($enabled) on device: $targetDeviceId")
            _playbackState.update { it.copy(shuffleEnabled = enabled) }
            apiService.setShuffle(enabled, deviceId = targetDeviceId)
            delay(200)
            syncRemotePlaybackState()
        }
    }

    override fun toggleShuffle() {
        setShuffle(!_playbackState.value.shuffleEnabled)
    }

    override fun setSmartShuffle(enabled: Boolean) {
        setShuffle(enabled)
    }

    override fun toggleSmartShuffle() {
        toggleShuffle()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        scope.launch {
            Log.d(TAG, "Connect setRepeatMode($mode) on device: $targetDeviceId")
            _playbackState.update { it.copy(repeatMode = mode) }
            val stateStr = when (mode) {
                RepeatMode.OFF -> "off"
                RepeatMode.ONE -> "track"
                RepeatMode.ALL -> "context"
            }
            apiService.setRepeat(stateStr, deviceId = targetDeviceId)
            delay(200)
            syncRemotePlaybackState()
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
        scope.launch {
            val percent = (volume * 100).toInt().coerceIn(0, 100)
            Log.d(TAG, "Connect setVolume($percent%) on device: $targetDeviceId")
            apiService.setVolume(percent, deviceId = targetDeviceId)
        }
    }

    override fun addToQueue(track: Track) {
        scope.launch {
            Log.d(TAG, "Connect addToQueue('${track.name}', uri=${track.uri}) on device: $targetDeviceId")
            apiService.addToQueue(track.uri, targetDeviceId)
        }
    }

    override fun restoreSession(lastTrack: Track, positionMs: Long) {}
}
