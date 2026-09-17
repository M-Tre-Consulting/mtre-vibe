package com.vibe.core.network.model

import com.vibe.core.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PagingResponseDto<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    val next: String? = null,
    val previous: String? = null
)

@Serializable
data class UserProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val email: String? = null,
    val product: String? = null,
    val images: List<ImageDto> = emptyList()
)

@Serializable
data class ImageDto(
    val url: String,
    val height: Int? = null,
    val width: Int? = null
)

@Serializable
data class ArtistSimpleDto(
    val id: String,
    val name: String,
    val uri: String
)

@Serializable
data class AlbumSimpleDto(
    val id: String,
    val name: String,
    val uri: String,
    val images: List<ImageDto> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("album_type") val albumType: String? = null,
    @SerialName("total_tracks") val totalTracks: Int? = null
)

@Serializable
data class TrackDto(
    val id: String,
    val uri: String,
    val name: String,
    @SerialName("duration_ms") val durationMs: Long,
    val artists: List<ArtistSimpleDto> = emptyList(),
    val album: AlbumSimpleDto? = null,
    val explicit: Boolean = false,
    @SerialName("is_playable") val isPlayable: Boolean? = true,
    @SerialName("preview_url") val previewUrl: String? = null
)

@Serializable
data class SavedTrackItemDto(
    @SerialName("added_at") val addedAt: String? = null,
    val track: TrackDto
)

@Serializable
data class PlaylistSimpleDto(
    val id: String,
    val uri: String,
    val name: String,
    val description: String? = null,
    val images: List<ImageDto> = emptyList(),
    val owner: UserProfileDto? = null,
    val collaborative: Boolean = false,
    val public: Boolean? = true,
    @SerialName("snapshot_id") val snapshotId: String? = null,
    val tracks: PlaylistTracksRefDto? = null
)

@Serializable
data class PlaylistTracksRefDto(
    val total: Int = 0,
    val href: String? = null
)

@Serializable
data class PlaylistTrackItemDto(
    @SerialName("added_at") val addedAt: String? = null,
    val track: TrackDto? = null
)

@Serializable
data class PlaylistDetailDto(
    val id: String,
    val uri: String,
    val name: String,
    val description: String? = null,
    val images: List<ImageDto> = emptyList(),
    val owner: UserProfileDto? = null,
    val collaborative: Boolean = false,
    val public: Boolean? = true,
    @SerialName("snapshot_id") val snapshotId: String? = null,
    val tracks: PagingResponseDto<PlaylistTrackItemDto> = PagingResponseDto()
)

@Serializable
data class SearchResponseDto(
    val tracks: PagingResponseDto<TrackDto>? = null,
    val artists: PagingResponseDto<ArtistFullDto>? = null,
    val albums: PagingResponseDto<AlbumSimpleDto>? = null,
    val playlists: PagingResponseDto<PlaylistSimpleDto>? = null
)

@Serializable
data class ArtistFullDto(
    val id: String,
    val uri: String,
    val name: String,
    val images: List<ImageDto> = emptyList(),
    val followers: FollowersDto? = null,
    val genres: List<String> = emptyList()
)

@Serializable
data class FollowersDto(
    val total: Long = 0L
)

@Serializable
data class ArtistTopTracksDto(
    val tracks: List<TrackDto> = emptyList()
)

@Serializable
data class DeviceDto(
    val id: String,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("is_restricted") val isRestricted: Boolean = false,
    val name: String,
    val type: String,
    @SerialName("volume_percent") val volumePercent: Int? = 100,
    @SerialName("supports_volume") val supportsVolume: Boolean = true
)

@Serializable
data class DeviceListResponseDto(
    val devices: List<DeviceDto> = emptyList()
)

// Mapping extensions to Domain models
fun TrackDto.toDomain(addedAtTimestamp: Long? = null, isLiked: Boolean = false): Track {
    val albumSummary = album?.let {
        AlbumSummary(
            id = it.id,
            name = it.name,
            uri = it.uri,
            imageUrl = it.images.firstOrNull()?.url,
            releaseDate = it.releaseDate,
            albumType = when {
                it.albumType.equals("ep", ignoreCase = true) -> AlbumType.EP
                it.albumType.equals("single", ignoreCase = true) -> {
                    // If track count is between 3 and 6, classify as EP
                    if ((it.totalTracks ?: 1) in 3..6) AlbumType.EP else AlbumType.SINGLE
                }
                it.albumType.equals("compilation", ignoreCase = true) -> AlbumType.COMPILATION
                else -> AlbumType.ALBUM
            }
        )
    } ?: AlbumSummary(id = "", name = "Unknown", uri = "")

    return Track(
        id = id,
        uri = uri,
        name = name,
        durationMs = durationMs,
        artists = artists.map { ArtistSummary(id = it.id, name = it.name, uri = it.uri) },
        album = albumSummary,
        isPlayable = isPlayable ?: true,
        isExplicit = explicit,
        isLiked = isLiked,
        addedAt = addedAtTimestamp,
        previewUrl = previewUrl
    )
}

fun DeviceDto.toDomain(): Device {
    val domainType = when (type.uppercase()) {
        "COMPUTER" -> DeviceType.COMPUTER
        "SMARTPHONE" -> DeviceType.SMARTPHONE
        "SPEAKER" -> DeviceType.SPEAKER
        "AVR" -> DeviceType.AVR
        "CASTAUDIO" -> DeviceType.CAST_AUDIO
        "CASTVIDEO" -> DeviceType.CAST_VIDEO
        else -> DeviceType.UNKNOWN
    }
    return Device(
        id = id,
        name = name,
        type = domainType,
        isActive = isActive,
        isLocal = false,
        isRestricted = isRestricted,
        volumePercent = volumePercent ?: 100,
        supportsVolume = supportsVolume
    )
}
