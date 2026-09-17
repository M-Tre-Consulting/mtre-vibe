package com.vibe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val uri: String,
    val name: String,
    val durationMs: Long,
    val artists: List<ArtistSummary>,
    val album: AlbumSummary,
    val isPlayable: Boolean = true,
    val isExplicit: Boolean = false,
    val isLiked: Boolean = false,
    val addedAt: Long? = null,
    val previewUrl: String? = null
)

@Serializable
data class ArtistSummary(
    val id: String,
    val name: String,
    val uri: String,
    val imageUrl: String? = null
)

@Serializable
data class AlbumSummary(
    val id: String,
    val name: String,
    val uri: String,
    val imageUrl: String? = null,
    val releaseDate: String? = null,
    val albumType: AlbumType = AlbumType.ALBUM
)
