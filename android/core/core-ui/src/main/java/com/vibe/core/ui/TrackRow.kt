package com.vibe.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vibe.core.model.Track
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    isSelected: Boolean = false,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    isCompact: Boolean = false,
    showArtwork: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onSwipeToQueue: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxSwipePx = with(density) { 140.dp.toPx() }

    val draggableState = rememberDraggableState { delta ->
        if (onSwipeToQueue != null) {
            coroutineScope.launch {
                val next = (offsetX.value + delta).coerceIn(0f, maxSwipePx)
                offsetX.snapTo(next)
            }
        }
    }

    val rowBg = when {
        isSelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        isCurrentTrack -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        offsetX.value > 0f -> MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = onSwipeToQueue != null,
                onDragStopped = { velocity ->
                    if (onSwipeToQueue != null && (offsetX.value >= thresholdPx || velocity > 600f)) {
                        onSwipeToQueue()
                    }
                    coroutineScope.launch {
                        offsetX.animateTo(
                            0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            )
    ) {
        // Revealed background on swipe-right: Spotify green with Queue icon and text
        if (offsetX.value > 0f) {
            val swipeProgress = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + 0.75f * swipeProgress),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.track_swipe_to_queue),
                        tint = if (swipeProgress > 0.6f) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size((18 + (6 * swipeProgress)).dp)
                    )
                    if (offsetX.value > 60f) {
                        Text(
                            text = stringResource(R.string.track_swipe_to_queue),
                            color = if (swipeProgress > 0.6f) Color.White else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Foreground row content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(8.dp))
                .background(rowBg)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = if (isCompact) 6.dp else 10.dp)
        ) {
            if (isCompact) {
                // One line per song with spaced separators between name, artists and added date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCurrentTrack) {
                            MaterialTheme.colorScheme.primary
                        } else if (track.isPlayable) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrentTrack) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AnimatedEqualizerBars(
                            isPlaying = isPlaying,
                            color = MaterialTheme.colorScheme.primary,
                            width = 13.dp,
                            height = 12.dp
                        )
                    }
                    Text(
                        text = "  ·  ",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = track.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrentTrack) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.8f, fill = false)
                    )
                    val addedAt = track.addedAt
                    if (addedAt != null) {
                        Text(
                            text = "  ·  ",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(addedAt))
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                    if (onAddToQueue != null) {
                        IconButton(
                            onClick = onAddToQueue,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = stringResource(R.string.queue_add_to_queue),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                // Standard two-line layout with optional artwork
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (showArtwork && !track.album.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.album.imageUrl,
                            contentDescription = track.name,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = track.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCurrentTrack) {
                                    MaterialTheme.colorScheme.primary
                                } else if (track.isPlayable) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isCurrentTrack) {
                                Spacer(modifier = Modifier.width(8.dp))
                                AnimatedEqualizerBars(
                                    isPlaying = isPlaying,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (track.isExplicit) {
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = "E",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = track.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrentTrack) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (onAddToQueue != null) {
                        IconButton(
                            onClick = onAddToQueue,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = stringResource(R.string.queue_add_to_queue),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
