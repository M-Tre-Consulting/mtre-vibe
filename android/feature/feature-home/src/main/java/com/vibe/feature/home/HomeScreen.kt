package com.vibe.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vibe.core.model.Playlist
import com.vibe.core.model.Track

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    playlists: List<Playlist> = emptyList(),
    recentTracks: List<Track> = emptyList(),
    onPlaylistClick: (String) -> Unit = {},
    onTrackClick: (Track, List<Track>) -> Unit = { _, _ -> }
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Made For You / User Playlists
        item {
            Text(
                stringResource(com.vibe.core.ui.R.string.home_made_for_you),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (playlists.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        ElevatedCard(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable { onPlaylistClick(playlist.id) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                if (!playlist.coverImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = playlist.coverImageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${playlist.totalTracks} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(5) { index ->
                        ElevatedCard(
                            modifier = Modifier.size(width = 140.dp, height = 180.dp),
                            onClick = { onPlaylistClick("playlist_$index") }
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                Text(
                                    stringResource(com.vibe.core.ui.R.string.home_mix_prefix, index + 1),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recently Played / Liked Tracks
        item {
            Text(
                stringResource(com.vibe.core.ui.R.string.home_recently_played),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (recentTracks.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recentTracks, key = { it.id }) { track ->
                        ElevatedCard(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable { onTrackClick(track, recentTracks) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                val coverUrl = track.album.imageUrl
                                if (!coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    track.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    track.artists.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(5) { index ->
                        ElevatedCard(
                            modifier = Modifier.size(width = 140.dp, height = 180.dp),
                            onClick = {}
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                Text(
                                    stringResource(com.vibe.core.ui.R.string.home_recent_prefix, index + 1),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
