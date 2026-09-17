package com.vibe.core.network

import com.vibe.core.model.*
import com.vibe.core.network.api.SpotifyRetrofitApi
import com.vibe.core.network.model.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class SpotifyApiServiceImpl(
    private val retrofitApi: SpotifyRetrofitApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SpotifyApiService {

    override suspend fun searchCatalog(query: String): Result<SearchResult> = withContext(ioDispatcher) {
        runCatching {
            val response = retrofitApi.search(query = query, type = "track,artist,album")
            if (!response.isSuccessful) {
                throw IOException("Search error HTTP ${response.code()}: ${response.errorBody()?.string()}")
            }

            val body = response.body()
            val tracks = body?.tracks?.items?.map { it.toDomain() } ?: emptyList()
            val artists = body?.artists?.items?.map {
                ArtistSummary(
                    id = it.id,
                    name = it.name,
                    uri = it.uri,
                    imageUrl = it.images.firstOrNull()?.url
                )
            } ?: emptyList()
            val albums = body?.albums?.items?.map {
                AlbumSummary(
                    id = it.id,
                    name = it.name,
                    uri = it.uri,
                    imageUrl = it.images.firstOrNull()?.url,
                    releaseDate = it.releaseDate
                )
            } ?: emptyList()

            SearchResult(
                topResult = tracks.firstOrNull(),
                tracks = tracks,
                artists = artists,
                albums = albums,
                playlists = emptyList(),
                isCatalogSuccess = true,
                isPlaylistSearchSuccess = true
            )
        }
    }

    override suspend fun searchPlaylists(query: String): Result<List<Playlist>> = withContext(ioDispatcher) {
        runCatching {
            val response = retrofitApi.search(query = query, type = "playlist")
            if (!response.isSuccessful) {
                throw IOException("Search playlists error HTTP ${response.code()}")
            }

            val playlistsDto = response.body()?.playlists?.items ?: emptyList()
            playlistsDto.map { dto ->
                Playlist(
                    id = dto.id,
                    uri = dto.uri,
                    name = dto.name,
                    description = dto.description,
                    coverImageUrl = dto.images.firstOrNull()?.url,
                    ownerName = dto.owner?.displayName ?: "Spotify",
                    ownerId = dto.owner?.id ?: "",
                    isCollaborative = dto.collaborative,
                    isPublic = dto.public ?: true,
                    totalTracks = dto.tracks?.total ?: 0,
                    snapshotId = dto.snapshotId
                )
            }
        }
    }

    override suspend fun getCurrentUserPlaylists(limit: Int, offset: Int): Result<List<Playlist>> = withContext(ioDispatcher) {
        runCatching {
            val response = retrofitApi.getCurrentUserPlaylists(limit = limit, offset = offset)
            if (!response.isSuccessful) {
                throw IOException("Get current user playlists error HTTP ${response.code()}")
            }

            val items = response.body()?.items ?: emptyList()
            items.map { dto ->
                Playlist(
                    id = dto.id,
                    uri = dto.uri,
                    name = dto.name,
                    description = dto.description,
                    coverImageUrl = dto.images.firstOrNull()?.url,
                    ownerName = dto.owner?.displayName ?: "User",
                    ownerId = dto.owner?.id ?: "",
                    isCollaborative = dto.collaborative,
                    isPublic = dto.public ?: true,
                    totalTracks = dto.tracks?.total ?: 0,
                    snapshotId = dto.snapshotId
                )
            }
        }
    }

    override suspend fun getPlaylist(id: String): Result<Playlist> = withContext(ioDispatcher) {
        runCatching {
            val response = retrofitApi.getPlaylist(id)
            if (!response.isSuccessful) {
                throw IOException("Get playlist error HTTP ${response.code()}")
            }

            val dto = response.body() ?: throw IOException("Empty playlist response")
            val tracks = dto.tracks.items.mapNotNull { it.track?.toDomain() }

            Playlist(
                id = dto.id,
                uri = dto.uri,
                name = dto.name,
                description = dto.description,
                coverImageUrl = dto.images.firstOrNull()?.url,
                ownerName = dto.owner?.displayName ?: "Spotify",
                ownerId = dto.owner?.id ?: "",
                isCollaborative = dto.collaborative,
                isPublic = dto.public ?: true,
                totalTracks = dto.tracks.total,
                snapshotId = dto.snapshotId,
                revision = dto.snapshotId,
                tracks = tracks
            )
        }
    }

    override suspend fun getArtist(id: String): Result<Artist> = withContext(ioDispatcher) {
        runCatching {
            val artistResp = retrofitApi.getArtist(id)
            val topTracksResp = retrofitApi.getArtistTopTracks(id)
            val albumsResp = retrofitApi.getArtistAlbums(id)

            val artistDto = artistResp.body() ?: throw IOException("Artist not found")
            val topTracks = topTracksResp.body()?.tracks?.map { it.toDomain() } ?: emptyList()
            val albumSummaries = albumsResp.body()?.items?.map {
                AlbumSummary(
                    id = it.id,
                    name = it.name,
                    uri = it.uri,
                    imageUrl = it.images.firstOrNull()?.url,
                    releaseDate = it.releaseDate,
                    albumType = when {
                        it.albumType.equals("ep", true) || (it.totalTracks in 3..6) -> AlbumType.EP
                        it.albumType.equals("single", true) -> AlbumType.SINGLE
                        it.albumType.equals("compilation", true) -> AlbumType.COMPILATION
                        else -> AlbumType.ALBUM
                    }
                )
            } ?: emptyList()

            Artist(
                id = artistDto.id,
                uri = artistDto.uri,
                name = artistDto.name,
                imageUrl = artistDto.images.firstOrNull()?.url,
                monthlyListeners = artistDto.followers?.total ?: 0L,
                topTracks = topTracks,
                albums = albumSummaries.filter { it.albumType == AlbumType.ALBUM },
                singlesAndEps = albumSummaries.filter { it.albumType == AlbumType.EP || it.albumType == AlbumType.SINGLE },
                compilations = albumSummaries.filter { it.albumType == AlbumType.COMPILATION }
            )
        }
    }

    override suspend fun getAlbum(id: String): Result<Album> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.getAlbum(id)
            if (!resp.isSuccessful) throw IOException("Get album HTTP ${resp.code()}")
            val dto = resp.body() ?: throw IOException("Empty album response")
            val tracks = dto.tracks.items.map { it.toDomain() }
            val albumType = when {
                dto.albumType.equals("ep", ignoreCase = true) || (dto.tracks.total in 3..6) -> AlbumType.EP
                dto.albumType.equals("single", ignoreCase = true) -> AlbumType.SINGLE
                dto.albumType.equals("compilation", ignoreCase = true) -> AlbumType.COMPILATION
                else -> AlbumType.ALBUM
            }
            Album(
                id = dto.id,
                uri = dto.uri,
                name = dto.name,
                artists = dto.artists.map { ArtistSummary(id = it.id, name = it.name, uri = it.uri) },
                tracks = tracks,
                totalTracks = dto.tracks.total,
                releaseDate = dto.releaseDate ?: "",
                coverImageUrl = dto.images.firstOrNull()?.url,
                albumType = albumType
            )
        }
    }

    override suspend fun getLikedSongs(offset: Int, limit: Int): Result<List<Track>> = withContext(ioDispatcher) {
        runCatching {
            val response = retrofitApi.getSavedTracks(limit = limit, offset = offset)
            if (!response.isSuccessful) {
                throw IOException("Get saved tracks error HTTP ${response.code()}")
            }
            val items = response.body()?.items ?: emptyList()
            items.map { it.track.toDomain(isLiked = true) }
        }
    }

    override suspend fun setLiked(trackId: String, isLiked: Boolean): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val resp = if (isLiked) {
                retrofitApi.saveTrack(trackId)
            } else {
                retrofitApi.removeSavedTrack(trackId)
            }
            if (!resp.isSuccessful) {
                throw IOException("Set liked error HTTP ${resp.code()}")
            }
        }
    }

    override suspend fun getLyrics(trackId: String): Result<Lyrics?> = withContext(ioDispatcher) {
        // Fallback or external synced lyrics provider
        Result.success(null)
    }

    override suspend fun getAvailableDevices(): Result<List<Device>> = withContext(ioDispatcher) {
        runCatching {
            val response = retrofitApi.getAvailableDevices()
            if (!response.isSuccessful) {
                throw IOException("Get devices error HTTP ${response.code()}")
            }
            val devices = response.body()?.devices ?: emptyList()
            devices.map { it.toDomain() }
        }
    }

    override suspend fun transferPlayback(deviceId: String, play: Boolean): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val body = mapOf("device_ids" to listOf(deviceId), "play" to play)
            val resp = retrofitApi.transferPlayback(body)
            if (!resp.isSuccessful) {
                throw IOException("Transfer playback error HTTP ${resp.code()}")
            }
        }
    }
}
