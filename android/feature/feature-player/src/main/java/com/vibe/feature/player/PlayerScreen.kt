package com.vibe.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vibe.core.model.PlaybackState

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onBarClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    val currentTrack = playbackState.currentTrack ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBarClick)
        ) {
            Column {
                // Seek / progress indicator
                val progress = if (playbackState.durationMs > 0) {
                    playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()
                } else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val coverArt = currentTrack.album.imageUrl
                    if (!coverArt.isNullOrBlank()) {
                        AsyncImage(
                            model = coverArt,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val firstArtist = currentTrack.artists.firstOrNull()
                        Text(
                            text = currentTrack.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                if (firstArtist != null) {
                                    onArtistClick(firstArtist.id)
                                }
                            }
                        )
                    }

                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = androidx.compose.ui.res.stringResource(
                                if (playbackState.isPlaying) com.vibe.core.ui.R.string.player_pause
                                else com.vibe.core.ui.R.string.player_play
                            ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onSkipNext) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_skip_next)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullPlayerScreen(
    playbackState: PlaybackState,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onClose: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenLyrics: () -> Unit = {},
    onOpenDevices: () -> Unit = {}
) {
    val track = playbackState.currentTrack ?: return
    var sliderPosition by remember(playbackState.positionMs) {
        mutableFloatStateOf(playbackState.positionMs.toFloat())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_collapse)
                )
            }
            Text(
                text = playbackState.contextTitle ?: androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_now_playing),
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(onClick = onOpenDevices) {
                Icon(
                    Icons.Default.SpeakerGroup,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_devices)
                )
            }
        }

        // Album Art Box
        Card(
            modifier = Modifier
                .size(300.dp)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            val coverArt = track.album.imageUrl
            if (!coverArt.isNullOrBlank()) {
                AsyncImage(
                    model = coverArt,
                    contentDescription = track.album.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp))
                }
            }
        }

        // Track Info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(track.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(
                track.artists.joinToString(", ") { it.name },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    track.artists.firstOrNull()?.let { onArtistClick(it.id) }
                }
            )
        }

        // Seek Bar (Seek discards queued audio buffer)
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onSeekTo(sliderPosition.toLong()) },
                valueRange = 0f..playbackState.durationMs.coerceAtLeast(1L).toFloat()
            )
            val formatMs: (Long) -> String = { ms ->
                val totalSecs = (ms / 1000).toInt()
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                "%d:%02d".format(mins, secs)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(sliderPosition.toLong()), style = MaterialTheme.typography.bodySmall)
                Text(formatMs(playbackState.durationMs), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSkipPrevious) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_skip_previous)
                )
            }
            FloatingActionButton(
                onClick = onPlayPause,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = androidx.compose.ui.res.stringResource(
                        if (playbackState.isPlaying) com.vibe.core.ui.R.string.player_pause
                        else com.vibe.core.ui.R.string.player_play
                    )
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_skip_next)
                )
            }
        }

        // Bottom Actions: Lyrics & Queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onOpenLyrics) {
                Icon(
                    Icons.Default.Lyrics,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_lyrics)
                )
            }
            IconButton(onClick = onOpenQueue) {
                Icon(
                    Icons.Default.QueueMusic,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_queue)
                )
            }
        }
    }
}
