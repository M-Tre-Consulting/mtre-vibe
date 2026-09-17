package com.vibe.feature.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibe.core.model.Playlist
import com.vibe.core.model.Track
import com.vibe.core.ui.TrackRow

@Composable
fun PlaylistScreen(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onPlayClick: () -> Unit = {},
    onTrackClick: (Track, Int) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onAddTrackToLiked: (Track) -> Unit = {}
) {
    val selectedTrackIds = remember { mutableStateListOf<String>() }

    Column(modifier = modifier.fillMaxSize()) {
        // Playlist Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(playlist.name, style = MaterialTheme.typography.headlineMedium)
            playlist.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                androidx.compose.ui.res.stringResource(
                    com.vibe.core.ui.R.string.playlist_songs_count,
                    playlist.ownerName,
                    playlist.totalTracks
                ),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.playlist_refresh)
                    )
                }

                FloatingActionButton(
                    onClick = {
                        // Starts explicitly at the first available song
                        onPlayClick()
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.playlist_play)
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.playlist_more_options)
                    )
                }
            }
        }

        // Tracklist
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(playlist.tracks, key = { _, track -> track.id }) { index, track ->
                val isSelected = selectedTrackIds.contains(track.id)
                TrackRow(
                    track = track,
                    isSelected = isSelected,
                    isCompact = isCompact,
                    onClick = { onTrackClick(track, index) },
                    onLongClick = {
                        if (isSelected) selectedTrackIds.remove(track.id)
                        else selectedTrackIds.add(track.id)
                    }
                )
            }
        }
    }
}
