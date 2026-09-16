package com.vibe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: String,
    val uri: String,
    val name: String,
    val imageUrl: String? = null,
    val monthlyListeners: Long = 0L,
    val isFollowed: Boolean = false,
    val topTracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val singlesAndEps: List<AlbumSummary> = emptyList(),
    val compilations: List<AlbumSummary> = emptyList(),
    val relatedArtists: List<ArtistSummary> = emptyList(),
    val bio: String? = null
)
