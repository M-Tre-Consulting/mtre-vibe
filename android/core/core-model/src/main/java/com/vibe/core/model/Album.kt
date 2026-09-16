package com.vibe.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AlbumType {
    ALBUM,
    EP,
    SINGLE,
    COMPILATION
}

@Serializable
data class Album(
    val id: String,
    val uri: String,
    val name: String,
    val artists: List<ArtistSummary>,
    val tracks: List<Track>,
    val totalTracks: Int,
    val releaseDate: String,
    val coverImageUrl: String?,
    val albumType: AlbumType,
    val copyright: String? = null
)
