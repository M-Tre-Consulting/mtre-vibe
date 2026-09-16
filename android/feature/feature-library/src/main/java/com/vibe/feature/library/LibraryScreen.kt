package com.vibe.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibe.core.model.LibrarySortOrder
import com.vibe.core.model.Playlist

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    playlists: List<Playlist> = emptyList(),
    onOpenLikedSongs: () -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onPlaylistDoubleClick: (Playlist) -> Unit = {},
    onSortOrderChange: (LibrarySortOrder) -> Unit = {}
) {
    var currentSortOrder by remember { mutableStateOf(LibrarySortOrder.RECENT_PLAYS) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your Library", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = {
                // Cycle sort order: Name -> Recent -> Date
                currentSortOrder = when (currentSortOrder) {
                    LibrarySortOrder.RECENT_PLAYS -> LibrarySortOrder.NAME
                    LibrarySortOrder.NAME -> LibrarySortOrder.SAVED_DATE
                    else -> LibrarySortOrder.RECENT_PLAYS
                }
                onSortOrderChange(currentSortOrder)
            }) {
                Icon(Icons.Default.Sort, contentDescription = "Sort order")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Liked Songs pinned tile
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenLikedSongs
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Liked Songs", style = MaterialTheme.typography.titleMedium)
                            Text("Auto-cached locally", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.PushPin, contentDescription = "Pinned", modifier = Modifier.size(18.dp))
                    }
                }
            }

            items(playlists, key = { it.id }) { playlist ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onPlaylistClick(playlist) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                            Text("${playlist.totalTracks} tracks", style = MaterialTheme.typography.bodySmall)
                        }
                        if (playlist.isPinned) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pinned", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
