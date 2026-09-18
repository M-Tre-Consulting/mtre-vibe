package com.vibe.core.network

import com.vibe.core.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class SearchResult(
    val topResult: Track? = null,
    val tracks: List<Track> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isCatalogSuccess: Boolean = true,
    val isPlaylistSearchSuccess: Boolean = true
)

interface SpotifyApiService {
    suspend fun searchCatalog(query: String): Result<SearchResult>
    suspend fun searchPlaylists(query: String): Result<List<Playlist>>
    suspend fun getCurrentUserPlaylists(limit: Int = 50, offset: Int = 0): Result<List<Playlist>>
    suspend fun getPlaylist(id: String): Result<Playlist>
    suspend fun getArtist(id: String): Result<Artist>
    suspend fun getAlbum(id: String): Result<Album>
    suspend fun getUserSavedAlbums(limit: Int = 50, offset: Int = 0): Result<List<AlbumSummary>>
    suspend fun getLikedSongs(offset: Int, limit: Int): Result<List<Track>>
    suspend fun setLiked(trackId: String, isLiked: Boolean): Result<Unit>
    suspend fun getLyrics(trackId: String): Result<Lyrics?>
    suspend fun getAvailableDevices(): Result<List<Device>>
    suspend fun transferPlayback(deviceId: String, play: Boolean): Result<Unit>
    suspend fun startPlayback(uris: List<String>? = null, contextUri: String? = null, deviceId: String? = null): Result<Unit>
    suspend fun pausePlayback(deviceId: String? = null): Result<Unit>
    suspend fun resumePlayback(deviceId: String? = null): Result<Unit>
    suspend fun getPlaybackState(): Result<PlaybackState?>
    suspend fun skipToNext(deviceId: String? = null): Result<Unit>
    suspend fun skipToPrevious(deviceId: String? = null): Result<Unit>
    suspend fun seekToPosition(positionMs: Long, deviceId: String? = null): Result<Unit>
    suspend fun setShuffle(enabled: Boolean, deviceId: String? = null): Result<Unit>
    suspend fun setRepeat(repeatMode: String, deviceId: String? = null): Result<Unit>
    suspend fun setVolume(volumePercent: Int, deviceId: String? = null): Result<Unit>
    suspend fun getUserQueue(): Result<Queue>
    suspend fun addToQueue(uri: String, deviceId: String? = null): Result<Unit>
    suspend fun getCurrentUserProfile(): Result<com.vibe.core.network.model.UserProfileDto>
}

/**
 * Executes dual-source search: personal catalogue + shared playlists concurrently,
 * ensuring failure in one source does not block results from the other.
 */
class DualSearchManager(
    private val apiService: SpotifyApiService
) {
    suspend fun executeSearch(query: String): SearchResult = coroutineScope {
        val catalogDeferred = async {
            runCatching { apiService.searchCatalog(query) }
        }
        val playlistDeferred = async {
            runCatching { apiService.searchPlaylists(query) }
        }

        val catalogResult = catalogDeferred.await().getOrNull()?.getOrNull()
        val playlistResult = playlistDeferred.await().getOrNull()?.getOrNull()

        SearchResult(
            topResult = catalogResult?.topResult,
            tracks = catalogResult?.tracks ?: emptyList(),
            artists = catalogResult?.artists ?: emptyList(),
            albums = catalogResult?.albums ?: emptyList(),
            playlists = playlistResult ?: emptyList(),
            isCatalogSuccess = catalogResult != null,
            isPlaylistSearchSuccess = playlistResult != null
        )
    }
}
