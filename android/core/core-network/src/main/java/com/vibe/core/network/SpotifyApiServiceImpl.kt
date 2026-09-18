package com.vibe.core.network

import com.vibe.core.model.*
import com.vibe.core.network.api.SpotifyRetrofitApi
import com.vibe.core.network.model.PlaylistDetailDto
import com.vibe.core.network.model.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class SpotifyApiServiceImpl(
    private val retrofitApi: SpotifyRetrofitApi,
    private val httpClient: OkHttpClient = OkHttpClient(),
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
                    releaseDate = it.releaseDate,
                    artistName = it.artists.joinToString(", ") { a -> a.name }
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
                    totalTracks = dto.items?.total ?: dto.tracks?.total ?: 0,
                    snapshotId = dto.snapshotId
                )
            }
        }
    }

    override suspend fun getPlaylist(id: String): Result<Playlist> = withContext(ioDispatcher) {
        runCatching {
            var playlistDto: PlaylistDetailDto? = null
            try {
                val response = retrofitApi.getPlaylist(id)
                if (response.isSuccessful) {
                    playlistDto = response.body()
                }
            } catch (_: Exception) {}

            var tracks = (playlistDto?.items?.items ?: playlistDto?.tracks?.items ?: emptyList())
                .mapNotNull { it.item?.toDomain() ?: it.track?.toDomain() }

            var total = playlistDto?.items?.total ?: playlistDto?.tracks?.total ?: tracks.size
            var name = playlistDto?.name ?: ""
            var desc = playlistDto?.description
            var cover = playlistDto?.images?.firstOrNull()?.url
            var owner = playlistDto?.owner?.displayName ?: "Spotify"
            var ownerId = playlistDto?.owner?.id ?: ""

            // If tracks is empty (Spotify returns 403 or empty tracks for public 3rd party playlists in Dev Mode),
            // seamlessly load full tracklist from the public embed entity
            if (tracks.isEmpty()) {
                val embedData = fetchEmbedEntity("playlist", id)
                if (embedData != null) {
                    if (name.isBlank()) name = embedData.name
                    if (desc.isNullOrBlank()) desc = embedData.description
                    if (cover.isNullOrBlank()) cover = embedData.coverImageUrl
                    if (embedData.tracks.isNotEmpty()) {
                        tracks = embedData.tracks
                        total = tracks.size
                    }
                }
            }

            Playlist(
                id = id,
                uri = playlistDto?.uri ?: "spotify:playlist:$id",
                name = name.ifBlank { "Playlist" },
                description = desc,
                coverImageUrl = cover,
                ownerName = owner,
                ownerId = ownerId,
                isCollaborative = playlistDto?.collaborative ?: false,
                isPublic = playlistDto?.public ?: true,
                totalTracks = total,
                snapshotId = playlistDto?.snapshotId,
                revision = playlistDto?.snapshotId,
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
                    },
                    artistName = it.artists.joinToString(", ") { a -> a.name }
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
            var albumDto: com.vibe.core.network.model.AlbumDetailDto? = null
            try {
                val resp = retrofitApi.getAlbum(id)
                if (resp.isSuccessful) {
                    albumDto = resp.body()
                }
            } catch (_: Exception) {}

            val albumType = when {
                albumDto?.albumType.equals("ep", ignoreCase = true) || ((albumDto?.tracks?.total ?: 0) in 3..6) -> AlbumType.EP
                albumDto?.albumType.equals("single", ignoreCase = true) -> AlbumType.SINGLE
                albumDto?.albumType.equals("compilation", ignoreCase = true) -> AlbumType.COMPILATION
                else -> AlbumType.ALBUM
            }

            val albumSummary = AlbumSummary(
                id = id,
                name = albumDto?.name ?: "",
                uri = albumDto?.uri ?: "spotify:album:$id",
                imageUrl = albumDto?.images?.firstOrNull()?.url,
                releaseDate = albumDto?.releaseDate,
                albumType = albumType,
                artistName = albumDto?.artists?.joinToString(", ") { it.name }
            )

            var tracks = albumDto?.tracks?.items?.map { trackDto ->
                trackDto.toDomain().copy(album = albumSummary)
            } ?: emptyList()

            var name = albumDto?.name ?: ""
            var cover = albumDto?.images?.firstOrNull()?.url
            var releaseDate = albumDto?.releaseDate ?: ""
            var artists = albumDto?.artists?.map { ArtistSummary(id = it.id, name = it.name, uri = it.uri) } ?: emptyList()
            var total = albumDto?.tracks?.total ?: tracks.size

            if (tracks.isEmpty()) {
                val embedData = fetchEmbedEntity("album", id)
                if (embedData != null) {
                    if (name.isBlank()) name = embedData.name
                    if (cover.isNullOrBlank()) cover = embedData.coverImageUrl
                    if (embedData.tracks.isNotEmpty()) {
                        tracks = embedData.tracks
                        total = tracks.size
                    }
                }
            }

            Album(
                id = id,
                uri = albumDto?.uri ?: "spotify:album:$id",
                name = name.ifBlank { "Album" },
                artists = artists,
                tracks = tracks,
                totalTracks = total,
                releaseDate = releaseDate,
                coverImageUrl = cover,
                albumType = albumType
            )
        }
    }

    override suspend fun getUserSavedAlbums(limit: Int, offset: Int): Result<List<AlbumSummary>> = withContext(ioDispatcher) {
        runCatching {
            val response = retrofitApi.getSavedAlbums(limit = limit, offset = offset)
            if (!response.isSuccessful) {
                throw IOException("Get saved albums error HTTP ${response.code()}")
            }
            val items = response.body()?.items ?: emptyList()
            items.map { item ->
                val dto = item.album
                AlbumSummary(
                    id = dto.id,
                    name = dto.name,
                    uri = dto.uri,
                    imageUrl = dto.images.firstOrNull()?.url,
                    releaseDate = dto.releaseDate,
                    albumType = when {
                        dto.albumType.equals("ep", true) -> AlbumType.EP
                        dto.albumType.equals("single", true) -> AlbumType.SINGLE
                        dto.albumType.equals("compilation", true) -> AlbumType.COMPILATION
                        else -> AlbumType.ALBUM
                    },
                    artistName = dto.artists.joinToString(", ") { it.name }
                )
            }
        }
    }

    private data class EmbedEntity(
        val name: String,
        val description: String?,
        val coverImageUrl: String?,
        val tracks: List<Track>
    )

    private fun fetchEmbedEntity(type: String, id: String): EmbedEntity? {
        return try {
            val request = Request.Builder()
                .url("https://open.spotify.com/embed/$type/$id")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null
            val scriptTagRegex = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val match = scriptTagRegex.find(html) ?: return null
            val jsonString = match.groupValues[1]

            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(jsonString)
            val state = root.jsonObject["props"]?.jsonObject?.get("pageProps")?.jsonObject?.get("state")?.jsonObject
            val entity = state?.get("data")?.jsonObject?.get("entity")?.jsonObject ?: return null

            val name = entity["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val subtitle = entity["subtitle"]?.jsonPrimitive?.contentOrNull
            val coverUrl = entity["coverArt"]?.jsonObject?.get("sources")?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                ?: entity["visualIdentity"]?.jsonObject?.get("image")?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

            val trackList = entity["trackList"]?.jsonArray ?: JsonArray(emptyList())
            val tracks = trackList.mapNotNull { trackElement ->
                val trackObj = trackElement.jsonObject
                val uri = trackObj["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val trackId = uri.substringAfterLast(":")
                val title = trackObj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
                val artistName = trackObj["subtitle"]?.jsonPrimitive?.contentOrNull ?: subtitle ?: "Unknown Artist"
                val duration = trackObj["duration"]?.jsonPrimitive?.longOrNull ?: 180000L
                val isPlayable = trackObj["isPlayable"]?.jsonPrimitive?.booleanOrNull ?: true
                val previewUrl = trackObj["audioPreview"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                Track(
                    id = trackId,
                    uri = uri,
                    name = title,
                    durationMs = duration,
                    artists = listOf(ArtistSummary(id = "", name = artistName, uri = "")),
                    album = AlbumSummary(id = id, name = name, uri = "spotify:$type:$id", imageUrl = coverUrl),
                    isPlayable = isPlayable,
                    previewUrl = previewUrl
                )
            }

            EmbedEntity(
                name = name,
                description = subtitle,
                coverImageUrl = coverUrl,
                tracks = tracks
            )
        } catch (_: Exception) {
            null
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

    override suspend fun getLyrics(
        trackId: String,
        trackName: String?,
        artistName: String?,
        durationSec: Int?
    ): Result<Lyrics?> = withContext(ioDispatcher) {
        runCatching {
            var title = trackName?.trim()
            var artist = artistName?.trim()
            var duration = durationSec

            // If title or artist is missing, fetch track info from Spotify API
            if (title.isNullOrBlank() || artist.isNullOrBlank()) {
                try {
                    val trackResp = retrofitApi.getTrack(trackId)
                    if (trackResp.isSuccessful) {
                        val body = trackResp.body()
                        title = body?.name
                        artist = body?.artists?.firstOrNull()?.name
                        duration = ((body?.durationMs ?: 0L) / 1000).toInt()
                    }
                } catch (_: Exception) {}
            }

            if (title.isNullOrBlank() || artist.isNullOrBlank()) {
                return@runCatching null
            }

            val cleanTitle = cleanLyricsQuery(title)
            val cleanArtist = artist.split(",", ";", "&", "feat.", "ft.").first().trim()

            // 1. Try LRCLIB exact get
            var lyrics = fetchFromLrclibGet(cleanTitle, cleanArtist, duration, trackId)

            // 2. If null, try LRCLIB search with cleaned query
            if (lyrics == null) {
                lyrics = fetchFromLrclibSearch("$cleanArtist $cleanTitle", trackId)
            }

            // 3. If still null and cleanTitle differed, try original title
            if (lyrics == null && cleanTitle != title) {
                lyrics = fetchFromLrclibSearch("$artist $title", trackId)
            }

            lyrics
        }
    }

    private fun cleanLyricsQuery(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*[\(\[](feat|ft|with|remaster|live|bonus|deluxe|version|radio|edit).*?[\)\]]"""), "")
            .replace(Regex("""(?i)\s*-\s*(remaster|live|bonus|deluxe|version|radio edit|edit).*$"""), "")
            .trim()
    }

    private fun fetchFromLrclibGet(title: String, artist: String, durationSec: Int?, trackId: String): Lyrics? {
        val urlBuilder = StringBuilder("https://lrclib.net/api/get?")
        urlBuilder.append("track_name=").append(java.net.URLEncoder.encode(title, "UTF-8"))
        urlBuilder.append("&artist_name=").append(java.net.URLEncoder.encode(artist, "UTF-8"))
        if (durationSec != null && durationSec > 0) {
            urlBuilder.append("&duration=").append(durationSec)
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("User-Agent", "VibeMusicApp/1.0 (https://github.com/vibe)")
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            parseLrclibResponse(body, trackId)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromLrclibSearch(query: String, trackId: String): Lyrics? {
        val url = "https://lrclib.net/api/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VibeMusicApp/1.0 (https://github.com/vibe)")
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = Json { ignoreUnknownKeys = true }
            val array = json.parseToJsonElement(body).jsonArray
            if (array.isEmpty()) return null
            val first = array.first().jsonObject
            parseLrclibObject(first, trackId)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLrclibResponse(jsonStr: String, trackId: String): Lyrics? {
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(jsonStr).jsonObject
        return parseLrclibObject(obj, trackId)
    }

    private fun parseLrclibObject(obj: kotlinx.serialization.json.JsonObject, trackId: String): Lyrics? {
        val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
        val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull

        if (!synced.isNullOrBlank()) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) {
                return Lyrics(
                    trackId = trackId,
                    isSynced = true,
                    lines = lines,
                    provider = "LRCLIB"
                )
            }
        }

        if (!plain.isNullOrBlank()) {
            val lines = plain.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { idx, line ->
                    LyricLine(timeMs = idx * 3000L, words = line)
                }
            if (lines.isNotEmpty()) {
                return Lyrics(
                    trackId = trackId,
                    isSynced = false,
                    lines = lines,
                    provider = "LRCLIB"
                )
            }
        }
        return null
    }

    private fun parseLrc(lrcText: String): List<LyricLine> {
        val lrcRegex = Regex("""^\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]\s*(.*)$""")
        val result = mutableListOf<LyricLine>()
        for (rawLine in lrcText.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val match = lrcRegex.find(trimmed) ?: continue
            val min = match.groupValues[1].toLongOrNull() ?: continue
            val sec = match.groupValues[2].toLongOrNull() ?: continue
            val msPart = match.groupValues[3]
            val fractionMs = when (msPart.length) {
                1 -> msPart.toLong() * 100
                2 -> msPart.toLong() * 10
                3 -> msPart.toLong()
                else -> 0L
            }
            val timeMs = min * 60_000L + sec * 1_000L + fractionMs
            val text = match.groupValues[4].trim()
            result.add(LyricLine(timeMs = timeMs, words = text))
        }
        return result.sortedBy { it.timeMs }
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

    override suspend fun startPlayback(
        uris: List<String>?,
        contextUri: String?,
        deviceId: String?
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val body = mutableMapOf<String, Any>()
            if (!uris.isNullOrEmpty()) {
                body["uris"] = uris
            } else if (!contextUri.isNullOrBlank()) {
                body["context_uri"] = contextUri
            }
            val resp = retrofitApi.play(deviceId = deviceId, body = if (body.isNotEmpty()) body else null)
            if (!resp.isSuccessful) {
                throw IOException("Play error HTTP ${resp.code()}: ${resp.errorBody()?.string()}")
            }
        }
    }

    override suspend fun pausePlayback(deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.pause(deviceId = deviceId)
            if (!resp.isSuccessful) {
                throw IOException("Pause error HTTP ${resp.code()}")
            }
        }
    }

    override suspend fun resumePlayback(deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.play(deviceId = deviceId, body = null)
            if (!resp.isSuccessful) {
                throw IOException("Resume error HTTP ${resp.code()}")
            }
        }
    }

    override suspend fun getPlaybackState(): Result<PlaybackState?> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.getPlaybackState()
            if (resp.code() == 204 || !resp.isSuccessful) {
                return@runCatching null
            }
            val dto = resp.body() ?: return@runCatching null
            val track = dto.item?.toDomain()
            val repeatMode = when (dto.repeatState) {
                "track" -> RepeatMode.ONE
                "context" -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
            PlaybackState(
                currentTrack = track,
                positionMs = dto.progressMs ?: 0L,
                durationMs = dto.item?.durationMs ?: 0L,
                isPlaying = dto.isPlaying,
                isPaused = !dto.isPlaying,
                shuffleEnabled = dto.shuffleState,
                repeatMode = repeatMode,
                activeDevice = dto.device?.toDomain()
            )
        }
    }

    override suspend fun skipToNext(deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.skipToNext(deviceId = deviceId)
            if (!resp.isSuccessful) {
                throw IOException("Skip next error HTTP ${resp.code()}")
            }
        }
    }

    override suspend fun skipToPrevious(deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.skipToPrevious(deviceId = deviceId)
            if (!resp.isSuccessful) {
                throw IOException("Skip previous error HTTP ${resp.code()}")
            }
        }
    }

    override suspend fun seekToPosition(positionMs: Long, deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.seek(positionMs = positionMs, deviceId = deviceId)
            if (!resp.isSuccessful) {
                throw IOException("Seek error HTTP ${resp.code()}")
            }
        }
    }

    override suspend fun setShuffle(enabled: Boolean, deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.setShuffle(state = enabled, deviceId = deviceId)
            if (!resp.isSuccessful) {
                throw IOException("Shuffle error HTTP ${resp.code()}")
            }
        }
    }

    override suspend fun setRepeat(repeatMode: String, deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.setRepeat(state = repeatMode, deviceId = deviceId)
            if (!resp.isSuccessful) {
                throw IOException("Repeat error HTTP ${resp.code()}")
            }
        }
    }

    override suspend fun setVolume(volumePercent: Int, deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.setVolume(volumePercent = volumePercent, deviceId = deviceId)
            if (!resp.isSuccessful) {
                throw IOException("Volume error HTTP ${resp.code()}")
            }
        }
    }

    override suspend fun getUserQueue(): Result<Queue> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.getQueue()
            if (!resp.isSuccessful) {
                throw IOException("Get queue error HTTP ${resp.code()}")
            }
            val body = resp.body()
            val current = body?.currentlyPlaying?.toDomain()
            val queuedTracks = body?.queue?.map { it.toDomain() } ?: emptyList()
            Queue(
                currentlyPlaying = current,
                userQueue = queuedTracks
            )
        }
    }

    override suspend fun addToQueue(uri: String, deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.addToQueue(uri = uri, deviceId = deviceId)
            if (!resp.isSuccessful) {
                throw IOException("Add to queue error HTTP ${resp.code()}")
            }
        }
    }

    override suspend fun getCurrentUserProfile(): Result<com.vibe.core.network.model.UserProfileDto> = withContext(ioDispatcher) {
        runCatching {
            val resp = retrofitApi.getCurrentUser()
            if (!resp.isSuccessful) {
                throw IOException("Get current user error HTTP ${resp.code()}")
            }
            resp.body() ?: throw IOException("User profile body is null")
        }
    }
}

