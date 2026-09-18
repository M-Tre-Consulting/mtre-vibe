package com.vibe.feature.album

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
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
import com.vibe.core.model.Album
import com.vibe.core.model.AlbumType
import com.vibe.core.model.RepeatMode
import com.vibe.core.model.Track
import com.vibe.core.ui.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    album: Album,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isShuffleActive: Boolean = false,
    isSmartShuffleActive: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    currentTrackId: String? = null,
    onBack: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onPlayClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onSmartShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onTrackClick: (Track, Int) -> Unit = { _, _ -> },
    onSwipeToQueue: ((Track) -> Unit)? = null,
    onTrackOptions: ((Track) -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = album.name,
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!album.coverImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = album.coverImageUrl,
                            contentDescription = album.name,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val typeLabel = when (album.albumType) {
                        AlbumType.EP -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_ep)
                        AlbumType.SINGLE -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_single)
                        AlbumType.COMPILATION -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_compilation)
                        AlbumType.ALBUM -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_album)
                    }
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            com.vibe.core.ui.R.string.album_info_format,
                            typeLabel,
                            album.releaseDate.take(4),
                            album.totalTracks
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Related Artist Pill / Row
                    if (album.artists.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .clickable {
                                    album.artists.firstOrNull()?.id?.let { onArtistClick(it) }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = album.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

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
                            // Shuffle Button
                            val shuffleBg by animateColorAsState(
                                targetValue = if (isShuffleActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                label = "albumShuffleBg"
                            )
                            val shuffleTint by animateColorAsState(
                                targetValue = if (isShuffleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "albumShuffleTint"
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
                                        contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_shuffle),
                                        tint = shuffleTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Smart Shuffle (AI-based) Button
                            val smartShuffleBg by animateColorAsState(
                                targetValue = if (isSmartShuffleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                label = "albumSmartShuffleBg"
                            )
                            val smartShuffleTint by animateColorAsState(
                                targetValue = if (isSmartShuffleActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "albumSmartShuffleTint"
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
                                        contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_smart_shuffle),
                                        tint = smartShuffleTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_smart_shuffle),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = smartShuffleTint
                                    )
                                }
                            }

                            // Repeat Button
                            val isRepeatActive = repeatMode != RepeatMode.OFF
                            val repeatBg by animateColorAsState(
                                targetValue = if (isRepeatActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                label = "albumRepeatBg"
                            )
                            val repeatTint by animateColorAsState(
                                targetValue = if (isRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "albumRepeatTint"
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
                                        contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_repeat),
                                        tint = repeatTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Right control: Signature Play/Pause FAB
                        FloatingActionButton(
                            onClick = onPlayClick,
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = androidx.compose.ui.res.stringResource(
                                    if (isPlaying) com.vibe.core.ui.R.string.player_pause
                                    else com.vibe.core.ui.R.string.player_play_album
                                ),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            itemsIndexed(album.tracks, key = { _, track -> track.id }) { index, track ->
                val isCurrent = track.id == currentTrackId
                TrackRow(
                    track = track,
                    isCurrentTrack = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    onClick = { onTrackClick(track, index) },
                    onLongClick = { onTrackOptions?.invoke(track) },
                    onSwipeToQueue = onSwipeToQueue?.let { cb -> { cb(track) } }
                )
            }
        }
    }
}
