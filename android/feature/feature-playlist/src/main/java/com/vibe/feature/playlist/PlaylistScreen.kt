package com.vibe.feature.playlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vibe.core.model.Playlist
import com.vibe.core.model.RepeatMode
import com.vibe.core.model.Track
import com.vibe.core.ui.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    isPlaying: Boolean = false,
    isShuffleActive: Boolean = false,
    isSmartShuffleActive: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    onBack: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onSmartShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onTrackClick: (Track, Int) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onAddTrackToLiked: (Track) -> Unit = {}
) {
    val selectedTrackIds = remember { mutableStateListOf<String>() }

    Column(modifier = modifier.fillMaxSize()) {
        // Navigation bar with Back button
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
                                .size(200.dp)
                                .clip(RoundedCornerShape(20.dp)),
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

                    Spacer(modifier = Modifier.height(20.dp))

                    // Material 3 Expressive Action Row: Shuffle, Smart Shuffle (AI), Repeat, Play/Pause
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left controls: Shuffle, Smart Shuffle (AI), Repeat
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Standard Shuffle Button
                            val shuffleBg by animateColorAsState(
                                targetValue = if (isShuffleActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                label = "shuffleBg"
                            )
                            val shuffleTint by animateColorAsState(
                                targetValue = if (isShuffleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "shuffleTint"
                            )
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = shuffleBg,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onShuffleClick() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = shuffleTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Smart Shuffle (AI-based) Button with Sparkle AutoAwesome badge
                            val smartShuffleBg by animateColorAsState(
                                targetValue = if (isSmartShuffleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                label = "smartShuffleBg"
                            )
                            val smartShuffleTint by animateColorAsState(
                                targetValue = if (isSmartShuffleActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "smartShuffleTint"
                            )
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = smartShuffleBg,
                                modifier = Modifier
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSmartShuffleClick() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Smart Shuffle (AI)",
                                        tint = smartShuffleTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Smart",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = smartShuffleTint
                                    )
                                }
                            }

                            // Repeat Button (OFF, ALL, ONE)
                            val isRepeatActive = repeatMode != RepeatMode.OFF
                            val repeatBg by animateColorAsState(
                                targetValue = if (isRepeatActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                label = "repeatBg"
                            )
                            val repeatTint by animateColorAsState(
                                targetValue = if (isRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "repeatTint"
                            )
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = repeatBg,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onRepeatClick() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                        contentDescription = "Repeat",
                                        tint = repeatTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Right control: Signature High-Emphasis Play/Pause Action Button
                        FloatingActionButton(
                            onClick = onPlayClick,
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.playlist_play),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
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
