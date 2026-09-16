package com.vibe.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.vibe.core.model.PlaybackState
import com.vibe.core.model.RepeatMode
import com.vibe.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class Media3AudioPlayerImpl(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : VibeAudioPlayer {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val audioCacheDir = File(context.cacheDir, "vibe_audio_cache")
    private val audioCache: SimpleCache by lazy {
        val evictor = LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024L) // 512MB disk audio cache
        SimpleCache(audioCacheDir, evictor)
    }

    private val upstreamDataSourceFactory = DefaultDataSource.Factory(context)
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
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _playbackState.update {
                        it.copy(
                            isBuffering = state == Player.STATE_BUFFERING,
                            isPaused = state == Player.STATE_READY && !isPlaying,
                            durationMs = duration.coerceAtLeast(0L)
                        )
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        // Confirmed local seek discards audio queued from the old position
                        _playbackState.update { it.copy(positionMs = newPosition.positionMs) }
                    }
                }
            })
        }

    override fun playTrack(track: Track, contextTracks: List<Track>) {
        // Immediate metadata display while connection resolves
        _playbackState.update {
            it.copy(
                currentTrack = track,
                isBuffering = true,
                isPlaying = false,
                positionMs = 0L,
                durationMs = track.durationMs
            )
        }

        val mediaItem = buildMediaItem(track)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun playFilteredCollection(tracks: List<Track>, startIndex: Int) {
        val playableTracks = tracks.filter { it.isPlayable }
        if (playableTracks.isEmpty()) return

        val validIndex = startIndex.coerceIn(0, playableTracks.lastIndex)
        val selected = playableTracks[validIndex]

        _playbackState.update {
            it.copy(
                currentTrack = selected,
                isBuffering = true,
                positionMs = 0L,
                durationMs = selected.durationMs
            )
        }

        val mediaItems = playableTracks.map { buildMediaItem(it) }
        exoPlayer.setMediaItems(mediaItems, validIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
        _playbackState.update { it.copy(isPlaying = false, isPaused = true) }
    }

    override fun resume() {
        exoPlayer.play()
        _playbackState.update { it.copy(isPlaying = true, isPaused = false) }
    }

    override fun stop() {
        exoPlayer.stop()
        _playbackState.update { it.copy(isPlaying = false, isPaused = false) }
    }

    override fun seekTo(positionMs: Long) {
        // Confirmed seek discards previously queued audio buffer from old position
        exoPlayer.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    override fun skipToNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        }
    }

    override fun skipToPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        } else {
            seekTo(0L)
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
        // Kept paused until explicit user play
    }

    private fun buildMediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.name)
            .setArtist(track.artists.joinToString(", ") { it.name })
            .setAlbumTitle(track.album.name)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.previewUrl ?: track.uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
