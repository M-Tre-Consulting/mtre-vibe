package com.vibe.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vibe.core.model.*

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
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
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
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = currentTrack.album.imageUrl to currentTrack.id,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)).togetherWith(
                                fadeOut(animationSpec = tween(200))
                            )
                        },
                        label = "MiniPlayerArtwork"
                    ) { (coverArt, _) ->
                        if (!coverArt.isNullOrBlank()) {
                            AsyncImage(
                                model = coverArt,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    AnimatedContent(
                        targetState = currentTrack,
                        transitionSpec = {
                            (slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(animationSpec = tween(250))).togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { -it / 2 },
                                    animationSpec = tween(150)
                                ) + fadeOut(animationSpec = tween(150))
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = "MiniPlayerText"
                    ) { targetTrack ->
                        Column {
                            Text(
                                text = targetTrack.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = targetTrack.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val activeDev = playbackState.activeDevice
                            val errorMsg = playbackState.lastErrorMessage
                            if (errorMsg != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 1.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = errorMsg,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else if (activeDev != null && activeDev.isRemote) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 1.dp)
                                ) {
                                    Icon(
                                        imageVector = when (activeDev.type) {
                                            DeviceType.COMPUTER -> Icons.Default.Computer
                                            DeviceType.SPEAKER -> Icons.Default.Speaker
                                            else -> Icons.Default.SpeakerGroup
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = activeDev.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onPlayPause,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (playbackState.isPlaying) com.vibe.core.ui.R.string.player_pause
                                    else com.vibe.core.ui.R.string.player_play
                                ),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onSkipNext) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(com.vibe.core.ui.R.string.player_skip_next),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Full Player Screen matching native Google / Pixel Player aesthetics.
 */
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
    onOpenDevices: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleLike: () -> Unit = {}
) {
    var lastKnownTrack by remember { mutableStateOf(playbackState.currentTrack) }
    LaunchedEffect(playbackState.currentTrack) {
        if (playbackState.currentTrack != null) {
            lastKnownTrack = playbackState.currentTrack
        }
    }
    val track = playbackState.currentTrack ?: lastKnownTrack ?: return
    var sliderPosition by remember(playbackState.positionMs) {
        mutableFloatStateOf(playbackState.positionMs.toFloat())
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // Landscape / Tablet Two-Column Layout
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Drag handle pill + Top Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }

                    PlayerTopBar(
                        contextTitle = playbackState.contextTitle,
                        activeDevice = playbackState.activeDevice,
                        onClose = onClose,
                        onOpenDevices = onOpenDevices,
                        onOpenQueue = onOpenQueue
                    )
                }

                // Two columns: Left Artwork, Right Info & Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Artwork (fits both width and height, perfect square, never overflows)
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        val artSize = minOf(maxWidth * 0.95f, maxHeight * 0.85f)
                        PlayerArtwork(
                            imageUrl = track.album.imageUrl,
                            trackId = track.id,
                            albumName = track.album.name,
                            modifier = Modifier.size(artSize)
                        )
                    }

                    // Right Column: Controls and Info
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PlayerTrackInfo(
                            track = track,
                            onArtistClick = onArtistClick,
                            onOpenLyrics = onOpenLyrics
                        )

                        PlayerProgressSection(
                            sliderPosition = sliderPosition,
                            durationMs = playbackState.durationMs,
                            onValueChange = { sliderPosition = it },
                            onSeekTo = onSeekTo
                        )

                        val activeDev = playbackState.activeDevice
                        if (activeDev != null && activeDev.isRemote) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                onClick = onOpenDevices
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = when (activeDev.type) {
                                            DeviceType.COMPUTER -> Icons.Default.Computer
                                            DeviceType.SPEAKER -> Icons.Default.Speaker
                                            else -> Icons.Default.SpeakerGroup
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(com.vibe.core.ui.R.string.devices_listening_on, activeDev.name),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        val errMsg = playbackState.lastErrorMessage
                        if (!errMsg.isNullOrBlank()) {
                            PlayerErrorBanner(errorMessage = errMsg)
                        }

                        PlayerControlsRow(
                            isPlaying = playbackState.isPlaying,
                            onSkipPrevious = onSkipPrevious,
                            onPlayPause = onPlayPause,
                            onSkipNext = onSkipNext,
                            buttonHeight = 56.dp
                        )

                        PlayerBottomStrip(
                            shuffleEnabled = playbackState.shuffleEnabled,
                            repeatMode = playbackState.repeatMode,
                            isLiked = track.isLiked,
                            onToggleShuffle = onToggleShuffle,
                            onToggleRepeat = onToggleRepeat,
                            onToggleLike = onToggleLike,
                            height = 48.dp
                        )
                    }
                }
            }
        } else {
            // Portrait Layout: 1 Column (carefully constrained to never overflow)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 4.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(2.dp)
                        )
                )

                // 1. Top Bar
                PlayerTopBar(
                    contextTitle = playbackState.contextTitle,
                    activeDevice = playbackState.activeDevice,
                    onClose = onClose,
                    onOpenDevices = onOpenDevices,
                    onOpenQueue = onOpenQueue
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Large Album Artwork (Square, strictly constrained to viewport bounds)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val artSize = minOf(maxWidth * 0.90f, maxHeight * 0.90f)
                    PlayerArtwork(
                        imageUrl = track.album.imageUrl,
                        trackId = track.id,
                        albumName = track.album.name,
                        modifier = Modifier.size(artSize)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Track Info + Lyrics Button
                PlayerTrackInfo(
                    track = track,
                    onArtistClick = onArtistClick,
                    onOpenLyrics = onOpenLyrics
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Progress Bar + Timestamps + Center Audio Quality Pill
                PlayerProgressSection(
                    sliderPosition = sliderPosition,
                    durationMs = playbackState.durationMs,
                    onValueChange = { sliderPosition = it },
                    onSeekTo = onSeekTo
                )

                val activeDev = playbackState.activeDevice
                if (activeDev != null && activeDev.isRemote) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        onClick = onOpenDevices
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = when (activeDev.type) {
                                    DeviceType.COMPUTER -> Icons.Default.Computer
                                    DeviceType.SPEAKER -> Icons.Default.Speaker
                                    else -> Icons.Default.SpeakerGroup
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(com.vibe.core.ui.R.string.devices_listening_on, activeDev.name),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                val errMsg = playbackState.lastErrorMessage
                if (!errMsg.isNullOrBlank()) {
                    PlayerErrorBanner(errorMessage = errMsg)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 5. Signature Material 3 Playback Controls (3 Wide Stadium Capsules)
                PlayerControlsRow(
                    isPlaying = playbackState.isPlaying,
                    onSkipPrevious = onSkipPrevious,
                    onPlayPause = onPlayPause,
                    onSkipNext = onSkipNext,
                    buttonHeight = 64.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Bottom Actions Strip (Shuffle, Repeat, Like)
                PlayerBottomStrip(
                    shuffleEnabled = playbackState.shuffleEnabled,
                    repeatMode = playbackState.repeatMode,
                    isLiked = track.isLiked,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat,
                    onToggleLike = onToggleLike,
                    height = 54.dp
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    contextTitle: String?,
    activeDevice: Device? = null,
    onClose: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRemote = activeDevice != null && activeDevice.isRemote
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
            onClick = onClose
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(com.vibe.core.ui.R.string.player_collapse),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text(
            text = contextTitle ?: stringResource(com.vibe.core.ui.R.string.player_now_playing),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isRemote) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp),
                onClick = onOpenDevices
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (activeDevice?.type) {
                            DeviceType.COMPUTER -> Icons.Default.Computer
                            DeviceType.SPEAKER -> Icons.Default.Speaker
                            else -> Icons.Default.SpeakerGroup
                        },
                        contentDescription = stringResource(com.vibe.core.ui.R.string.player_devices),
                        tint = if (isRemote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp),
                onClick = onOpenQueue
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(com.vibe.core.ui.R.string.player_queue),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerArtwork(
    imageUrl: String?,
    trackId: String,
    albumName: String,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = imageUrl to trackId,
        transitionSpec = {
            (fadeIn(animationSpec = tween(400)) + scaleIn(
                initialScale = 0.90f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )).togetherWith(
                fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 1.05f)
            )
        },
        label = "FullPlayerArtwork"
    ) { (coverArt, _) ->
        Card(
            modifier = modifier.aspectRatio(1f),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            if (!coverArt.isNullOrBlank()) {
                AsyncImage(
                    model = coverArt,
                    contentDescription = albumName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTrackInfo(
    track: Track,
    onArtistClick: (String) -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = track,
            transitionSpec = {
                (slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(animationSpec = tween(300))).togetherWith(
                    slideOutVertically(
                        targetOffsetY = { -it / 3 },
                        animationSpec = tween(200)
                    ) + fadeOut(animationSpec = tween(200))
                )
            },
            modifier = Modifier.weight(1f),
            label = "FullPlayerTrackInfo"
        ) { targetTrack ->
            Column {
                Text(
                    text = targetTrack.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = targetTrack.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        targetTrack.artists.firstOrNull()?.let { onArtistClick(it.id) }
                    }
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(44.dp),
            onClick = onOpenLyrics
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = stringResource(com.vibe.core.ui.R.string.player_lyrics),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerProgressSection(
    sliderPosition: Float,
    durationMs: Long,
    onValueChange: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatMs: (Long) -> String = { ms ->
        val totalSecs = (ms / 1000).toInt()
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        "%02d:%02d".format(mins, secs)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderPosition,
            onValueChange = onValueChange,
            onValueChangeFinished = { onSeekTo(sliderPosition.toLong()) },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatMs(sliderPosition.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = stringResource(com.vibe.core.ui.R.string.player_audio_spec_default),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            Text(
                text = formatMs(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerControlsRow(
    isPlaying: Boolean,
    onSkipPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    buttonHeight: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous Button
        val prevInteraction = remember { MutableInteractionSource() }
        val prevPressed by prevInteraction.collectIsPressedAsState()
        val prevScale by animateFloatAsState(
            targetValue = if (prevPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "prevScale"
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight)
                .scale(prevScale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            interactionSource = prevInteraction,
            onClick = onSkipPrevious
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(com.vibe.core.ui.R.string.player_skip_previous),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Play / Pause Button
        val playInteraction = remember { MutableInteractionSource() }
        val playPressed by playInteraction.collectIsPressedAsState()
        val playScale by animateFloatAsState(
            targetValue = if (playPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "playScale"
        )
        Surface(
            modifier = Modifier
                .weight(1.18f)
                .height(buttonHeight)
                .scale(playScale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            interactionSource = playInteraction,
            onClick = onPlayPause
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) com.vibe.core.ui.R.string.player_pause
                        else com.vibe.core.ui.R.string.player_play
                    ),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Next Button
        val nextInteraction = remember { MutableInteractionSource() }
        val nextPressed by nextInteraction.collectIsPressedAsState()
        val nextScale by animateFloatAsState(
            targetValue = if (nextPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "nextScale"
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight)
                .scale(nextScale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            interactionSource = nextInteraction,
            onClick = onSkipNext
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(com.vibe.core.ui.R.string.player_skip_next),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerBottomStrip(
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isLiked: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    height: Dp = 54.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = stringResource(com.vibe.core.ui.R.string.player_shuffle),
                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleRepeat) {
                Icon(
                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = stringResource(com.vibe.core.ui.R.string.player_repeat),
                    tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(com.vibe.core.ui.R.string.player_favorite),
                    tint = if (isLiked) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlayerErrorBanner(
    errorMessage: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


