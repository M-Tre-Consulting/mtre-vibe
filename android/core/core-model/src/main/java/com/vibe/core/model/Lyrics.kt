package com.vibe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Lyrics(
    val trackId: String,
    val isSynced: Boolean,
    val lines: List<LyricLine> = emptyList(),
    val provider: String? = null
)

@Serializable
data class LyricLine(
    val timeMs: Long,
    val words: String
)

@Serializable
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Serializable
enum class LibrarySortOrder {
    NAME,
    RECENT_PLAYS,
    SAVED_DATE,
    CUSTOM_ORDER
}

@Serializable
data class UserSettings(
    val isCompactTrackList: Boolean = false,
    val isAlbumArtTintEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isVolumeNormalizationEnabled: Boolean = true,
    val maxAudioBitrateKbps: Int = 320,
    val isAudioDiskCacheEnabled: Boolean = true,
    val defaultSortOrder: LibrarySortOrder = LibrarySortOrder.RECENT_PLAYS
)
