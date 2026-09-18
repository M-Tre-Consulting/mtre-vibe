package com.vibe.core.network.api

import com.vibe.core.network.model.*
import retrofit2.Response
import retrofit2.http.*

interface SpotifyRetrofitApi {

    @GET("v1/me")
    suspend fun getCurrentUser(): Response<UserProfileDto>

    @GET("v1/me/playlists")
    suspend fun getCurrentUserPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<PagingResponseDto<PlaylistSimpleDto>>

    @GET("v1/playlists/{playlist_id}")
    suspend fun getPlaylist(
        @Path("playlist_id") playlistId: String
    ): Response<PlaylistDetailDto>

    @GET("v1/me/tracks")
    suspend fun getSavedTracks(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<PagingResponseDto<SavedTrackItemDto>>

    @PUT("v1/me/tracks")
    suspend fun saveTrack(
        @Query("ids") ids: String
    ): Response<Unit>

    @DELETE("v1/me/tracks")
    suspend fun removeSavedTrack(
        @Query("ids") ids: String
    ): Response<Unit>

    @GET("v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "track,artist,album,playlist",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<SearchResponseDto>

    @GET("v1/artists/{id}")
    suspend fun getArtist(
        @Path("id") artistId: String
    ): Response<ArtistFullDto>

    @GET("v1/artists/{id}/top-tracks")
    suspend fun getArtistTopTracks(
        @Path("id") artistId: String,
        @Query("market") market: String = "from_token"
    ): Response<ArtistTopTracksDto>

    @GET("v1/artists/{id}/albums")
    suspend fun getArtistAlbums(
        @Path("id") artistId: String,
        @Query("include_groups") includeGroups: String = "album,single,compilation",
        @Query("limit") limit: Int = 50
    ): Response<PagingResponseDto<AlbumSimpleDto>>

    @GET("v1/albums/{id}")
    suspend fun getAlbum(
        @Path("id") albumId: String
    ): Response<AlbumDetailDto>

    @GET("v1/me/albums")
    suspend fun getSavedAlbums(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<PagingResponseDto<SavedAlbumItemDto>>

    @GET("v1/me/player/devices")
    suspend fun getAvailableDevices(): Response<DeviceListResponseDto>

    @PUT("v1/me/player")
    suspend fun transferPlayback(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    @PUT("v1/me/player/play")
    suspend fun play(
        @Query("device_id") deviceId: String? = null,
        @Body body: Map<String, @JvmSuppressWildcards Any>? = null
    ): Response<Unit>

    @PUT("v1/me/player/pause")
    suspend fun pause(
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @POST("v1/me/player/next")
    suspend fun skipToNext(
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @POST("v1/me/player/previous")
    suspend fun skipToPrevious(
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @PUT("v1/me/player/seek")
    suspend fun seek(
        @Query("position_ms") positionMs: Long,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @PUT("v1/me/player/volume")
    suspend fun setVolume(
        @Query("volume_percent") volumePercent: Int,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @POST("v1/me/player/queue")
    suspend fun addToQueue(
        @Query("uri") uri: String,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @GET("v1/me/player/queue")
    suspend fun getQueue(): Response<QueueResponseDto>

    @GET("v1/me/player")
    suspend fun getPlaybackState(): Response<PlaybackResponseDto>

    @PUT("v1/me/player/shuffle")
    suspend fun setShuffle(
        @Query("state") state: Boolean,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @PUT("v1/me/player/repeat")
    suspend fun setRepeat(
        @Query("state") state: String,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>
}
