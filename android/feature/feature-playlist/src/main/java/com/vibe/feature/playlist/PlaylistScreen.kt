package com.vibe.feature.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vibe.core.model.Playlist
import com.vibe.core.model.Track
import com.vibe.core.ui.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onBack: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onTrackClick: (Track, Int) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onAddTrackToLiked: (Track) -> Unit = {}
) {
    val selectedTrackIds = remember { mutableStateListOf<String>() }

    Column(modifier = modifier.fillMaxSize()) {
        // Navigation bar with visible Back button
        TopAppBar(
            title = {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_back)
                    )
                }
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.playlist_refresh)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                // Playlist Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!playlist.coverImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = playlist.coverImageUrl,
                            contentDescription = playlist.name,
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    playlist.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            com.vibe.core.ui.R.string.playlist_songs_count,
                            playlist.ownerName,
                            playlist.totalTracks
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingActionButton(
                            onClick = onPlayClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
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

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Tracklist
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
