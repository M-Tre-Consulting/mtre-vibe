package com.vibe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val uri: String,
    val name: String,
    val description: String? = null,
    val coverImageUrl: String? = null,
    val ownerName: String,
    val ownerId: String,
    val isCollaborative: Boolean = false,
    val isPublic: Boolean = false,
    val isEditable: Boolean = false,
    val isPinned: Boolean = false,
    val pinPosition: Int? = null,
    val totalTracks: Int = 0,
    val snapshotId: String? = null,
    val revision: String? = null,
    val hasPendingEdits: Boolean = false,
    val tracks: List<Track> = emptyList()
)

@Serializable
data class PlaylistEditOperation(
    val playlistId: String,
    val type: EditType,
    val trackUris: List<String>,
    val targetIndex: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class EditType {
        ADD,
        REMOVE,
        REORDER,
        RENAME,
        CHANGE_DESCRIPTION,
        CHANGE_COVER
    }
}
