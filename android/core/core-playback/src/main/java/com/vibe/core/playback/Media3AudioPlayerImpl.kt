package com.vibe.core.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.vibe.core.model.PlaybackState
import com.vibe.core.model.RepeatMode
import com.vibe.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * 100% Native on-device Media3 ExoPlayer implementation.
 * Guarantees local playback through phone speakers / headphones with foreground
 * notification media session, offline cache, and multi-source audio resolver.
 */
@OptIn(UnstableApi::class)
class Media3AudioPlayerImpl(
    private val context: Context,
    private val tokenProvider: (() -> String?)? = null,
    private val remotePlaybackProvider: (suspend (List<String>) -> Result<Unit>)? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : VibeAudioPlayer {

    companion object {
        private const val TAG = "Media3AudioPlayer"
    }

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentPlaylist: List<Track> = emptyList()
    private var currentTrackIndex: Int = -1

    private val audioCacheDir = File(context.cacheDir, "vibe_audio_cache")
    private val audioCache: SimpleCache by lazy {
        val evictor = LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024L) // 512MB disk audio cache
        SimpleCache(audioCacheDir, evictor)
    }

    private val httpDataSourceFactory by lazy {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
    }

    private val upstreamDataSourceFactory by lazy {
        DefaultDataSource.Factory(context, httpDataSourceFactory)
    }

    private val cacheDataSourceFactory by lazy {
        CacheDataSource.Factory()
            .setCache(audioCache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 2_500,
                    /* maxBufferMs = */ 10_000,
                    /* bufferForPlaybackMs = */ 500,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_500
                )
                .build()
        )
        .build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playbackState.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) {
                        ensureMediaServiceStarted()
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _playbackState.update {
                        it.copy(
                            isBuffering = state == Player.STATE_BUFFERING,
                            isPaused = state == Player.STATE_READY && !isPlaying,
                            durationMs = duration.coerceAtLeast(0L)
                        )
                    }

                    if (state == Player.STATE_ENDED) {
                        if (_playbackState.value.repeatMode == RepeatMode.ONE) {
                            seekTo(0L)
                            play()
                        } else {
                            skipToNext()
                        }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        _playbackState.update { it.copy(positionMs = newPosition.positionMs) }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "Local ExoPlayer playback error: ${error.errorCodeName} - ${error.message}", error)
                    _playbackState.update {
                        it.copy(
                            isBuffering = false,
                            isPlaying = false,
                            isPaused = true
                        )
                    }
                }
            })
        }

    private val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
        override fun getAvailableCommands(): Player.Commands {
            val base = super.getAvailableCommands().buildUpon()
            base.add(Player.COMMAND_SEEK_TO_NEXT)
            base.add(Player.COMMAND_SEEK_TO_PREVIOUS)
            return base.build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            if (command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_PREVIOUS) return true
            return super.isCommandAvailable(command)
        }

        override fun seekToNext() {
            skipToNext()
        }

        override fun seekToNextMediaItem() {
            skipToNext()
        }

        override fun seekToPrevious() {
            skipToPrevious()
        }

        override fun seekToPreviousMediaItem() {
            skipToPrevious()
        }
    }

    val mediaSession: MediaSession by lazy {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        MediaSession.Builder(context, forwardingPlayer)
            .setId("VibeMediaSession")
            .apply {
                if (pendingIntent != null) {
                    setSessionActivity(pendingIntent)
                }
            }
            .build()
    }

    private fun ensureMediaServiceStarted() {
        try {
            val intent = Intent().setClassName(
                context.packageName,
                "com.vibe.app.playback.VibeMediaSessionService"
            )
            // Start the service normally. MediaSessionService handles entering foreground
            // safely with its internal MediaNotificationManager without hitting the 30s timeout.
            context.startService(intent)
        } catch (e: Exception) {
            Log.d(TAG, "Media service start attempt: ${e.message}")
        }
    }

    init {
        // Continuous position tracker ticker for smooth UI progress
        scope.launch {
            while (isActive) {
                delay(250)
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration
                    _playbackState.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = if (dur > 0L) dur else it.durationMs
                        )
                    }
                }
            }
        }
    }

    override fun playTrack(track: Track, contextTracks: List<Track>) {
        currentPlaylist = contextTracks.ifEmpty { listOf(track) }
        currentTrackIndex = currentPlaylist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playTrackInternal(track)
    }

    override fun playFilteredCollection(tracks: List<Track>, startIndex: Int) {
        val playableTracks = tracks.filter { it.isPlayable }
        if (playableTracks.isEmpty()) return

        currentPlaylist = playableTracks
        val validIndex = startIndex.coerceIn(0, playableTracks.lastIndex)
        currentTrackIndex = validIndex
        playTrackInternal(playableTracks[validIndex])
    }

    private fun playTrackInternal(track: Track) {
        _playbackState.update {
            it.copy(
                currentTrack = track,
                isBuffering = true,
                isPlaying = false,
                positionMs = 0L,
                durationMs = track.durationMs
            )
        }

        scope.launch {
            val resolvedUrl = TrackAudioResolver.resolveAudioUrl(track)
            if (!resolvedUrl.isNullOrBlank()) {
                Log.d(TAG, "Playing track locally on device: ${track.name} from $resolvedUrl")
                val mediaItem = buildMediaItem(track, resolvedUrl)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
            } else {
                Log.e(TAG, "Unable to resolve playable audio stream for ${track.name}")
                _playbackState.update {
                    it.copy(
                        isBuffering = false,
                        isPlaying = false,
                        isPaused = true
                    )
                }
            }

            // Prefetch next track audio URL in background
            val nextIdx = currentTrackIndex + 1
            if (nextIdx < currentPlaylist.size) {
                val nextTrack = currentPlaylist[nextIdx]
                launch(Dispatchers.IO) {
                    TrackAudioResolver.resolveAudioUrl(nextTrack)
                }
            }
        }
    }

    override fun pause() {
        exoPlayer.pause()
        _playbackState.update { it.copy(isPlaying = false, isPaused = true) }
    }

    override fun resume() {
        if (exoPlayer.currentMediaItem != null) {
            exoPlayer.play()
            _playbackState.update { it.copy(isPlaying = true, isPaused = false) }
        } else {
            _playbackState.value.currentTrack?.let { current ->
                playTrack(current, emptyList())
            }
        }
    }

    override fun stop() {
        exoPlayer.stop()
        _playbackState.update { it.copy(isPlaying = false, isPaused = false) }
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    override fun skipToNext() {
        if (currentPlaylist.isEmpty()) return
        val nextIndex = currentTrackIndex + 1
        if (nextIndex < currentPlaylist.size) {
            currentTrackIndex = nextIndex
            playTrackInternal(currentPlaylist[nextIndex])
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL) {
            currentTrackIndex = 0
            playTrackInternal(currentPlaylist[0])
        }
    }

    override fun skipToPrevious() {
        if (exoPlayer.currentPosition > 3000L) {
            exoPlayer.seekTo(0L)
            _playbackState.update { it.copy(positionMs = 0L) }
            return
        }
        if (currentPlaylist.isEmpty()) return
        val prevIndex = (currentTrackIndex - 1).coerceAtLeast(0)
        if (prevIndex != currentTrackIndex) {
            currentTrackIndex = prevIndex
            playTrackInternal(currentPlaylist[prevIndex])
        } else {
            exoPlayer.seekTo(0L)
            _playbackState.update { it.copy(positionMs = 0L) }
        }
    }

    override fun setShuffle(enabled: Boolean) {
        exoPlayer.shuffleModeEnabled = enabled
        _playbackState.update { it.copy(shuffleEnabled = enabled) }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        exoPlayer.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        _playbackState.update { it.copy(repeatMode = mode) }
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer.volume = clamped
        _playbackState.update { it.copy(volume = clamped) }
    }

    override fun restoreSession(lastTrack: Track, positionMs: Long) {
        _playbackState.update {
            it.copy(
                currentTrack = lastTrack,
                positionMs = positionMs,
                durationMs = lastTrack.durationMs,
                isPlaying = false,
                isPaused = true
            )
        }
        val mediaItem = buildMediaItem(lastTrack)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.seekTo(positionMs)
        exoPlayer.prepare()
    }

    private fun buildMediaItem(track: Track, overrideUrl: String? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.name)
            .setArtist(track.artists.joinToString(", ") { it.name })
            .setAlbumTitle(track.album.name)
            .build()

        val uriString = overrideUrl ?: track.previewUrl ?: track.uri
        val mimeType = when {
            uriString.contains(".m4a", ignoreCase = true) || uriString.contains(".mp4", ignoreCase = true) -> MimeTypes.AUDIO_AAC
            uriString.contains(".mp3", ignoreCase = true) || uriString.contains("p.scdn.co", ignoreCase = true) -> MimeTypes.AUDIO_MPEG
            uriString.contains(".ogg", ignoreCase = true) -> MimeTypes.AUDIO_OGG
            else -> null
        }

        val builder = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uriString)
            .setMediaMetadata(metadata)

        if (mimeType != null) {
            builder.setMimeType(mimeType)
        }

        return builder.build()
    }
}
