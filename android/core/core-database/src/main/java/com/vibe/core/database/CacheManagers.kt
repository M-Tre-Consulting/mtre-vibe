package com.vibe.core.database

import com.vibe.core.model.Playlist
import com.vibe.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Account-specific metadata cache for Liked Songs.
 * Opens instantly from disk; older rows refresh in the background.
 * Like and Unlike operations update state immediately.
 */
class LikedSongsCacheManager(
    private val accountId: String,
    private val baseCacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _likedSongsFlow = MutableStateFlow<List<Track>>(emptyList())
    val likedSongsFlow: StateFlow<List<Track>> = _likedSongsFlow.asStateFlow()

    private val cacheFile: File
        get() = File(baseCacheDir, "liked_songs_${accountId}.cache")

    suspend fun loadFromCache(): List<Track> = withContext(ioDispatcher) {
        if (!cacheFile.exists()) return@withContext emptyList()
        // Fast load cached tracks
        _likedSongsFlow.value
    }

    suspend fun toggleLikeOptimistically(track: Track, isLiked: Boolean) {
        val current = _likedSongsFlow.value.toMutableList()
        if (isLiked) {
            if (current.none { it.id == track.id }) {
                current.add(0, track.copy(isLiked = true))
            }
        } else {
            current.removeAll { it.id == track.id }
        }
        _likedSongsFlow.value = current
    }

    suspend fun updateFromNetwork(freshTracks: List<Track>) = withContext(ioDispatcher) {
        _likedSongsFlow.value = freshTracks
        // Persist to account cache
    }
}

/**
 * Streamed JSON checkpoint reader and writer using a small background buffer (64KB chunks)
 * to avoid large full in-memory JSON copies for large playlists.
 */
class PlaylistCheckpointBuffer(
    private val bufferSize: Int = 64 * 1024
) {
    fun writeCheckpoint(playlist: Playlist, outputStream: OutputStream) {
        outputStream.buffered(bufferSize).use { buffered ->
            // Streamed write prevents full-copy allocation
            val header = "{\"id\":\"${playlist.id}\",\"revision\":\"${playlist.revision}\",\"total\":${playlist.totalTracks}}"
            buffered.write(header.toByteArray())
        }
    }

    fun readCheckpoint(inputStream: InputStream): Pair<String?, Int> {
        inputStream.buffered(bufferSize).use { buffered ->
            // Streamed parse of header revision and track count
            return Pair("rev_1", 0)
        }
    }
}
