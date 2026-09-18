package com.vibe.feature.queue

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.vibe.core.model.Queue
import com.vibe.core.model.Track
import com.vibe.core.ui.TrackRow

@Composable
fun QueueScreen(
    queue: Queue,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    onRemoveFromQueue: (Track) -> Unit = {},
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit = {}
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 62.dp.toPx() }

    val hasUserQueue = queue.userQueue.isNotEmpty()
    val upcomingTracks = if (hasUserQueue) queue.userQueue else queue.contextQueue

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(com.vibe.core.ui.R.string.queue_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(com.vibe.core.ui.R.string.queue_close)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Currently Playing
        queue.currentlyPlaying?.let { current ->
            Text(
                stringResource(com.vibe.core.ui.R.string.player_now_playing),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            queue.contextName?.let {
                Text(
                    stringResource(com.vibe.core.ui.R.string.queue_from, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TrackRow(
                track = current,
                isCurrentTrack = true,
                isPlaying = true,
                showArtwork = true,
                onClick = { onTrackClick(current) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (queue.currentlyPlaying == null && upcomingTracks.isEmpty() && queue.recentHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(com.vibe.core.ui.R.string.queue_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Interactive reorderable upcoming queue
                if (upcomingTracks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (hasUserQueue) stringResource(com.vibe.core.ui.R.string.queue_next)
                                else stringResource(com.vibe.core.ui.R.string.queue_next_from, queue.contextName ?: ""),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onClearQueue) {
                                Text(
                                    stringResource(com.vibe.core.ui.R.string.queue_clear),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    itemsIndexed(upcomingTracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                        val isDragging = draggingIndex == index
                        val dragHandleModifier = Modifier.pointerInput(index, upcomingTracks.size) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val cur = draggingIndex ?: return@detectDragGestures
                                    if (dragOffsetY > itemHeightPx * 0.7f && cur < upcomingTracks.size - 1) {
                                        onMoveQueueItem(cur, cur + 1)
                                        draggingIndex = cur + 1
                                        dragOffsetY -= itemHeightPx
                                    } else if (dragOffsetY < -itemHeightPx * 0.7f && cur > 0) {
                                        onMoveQueueItem(cur, cur - 1)
                                        draggingIndex = cur - 1
                                        dragOffsetY += itemHeightPx
                                    }
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                }
                            )
                        }

                        QueueItemRow(
                            track = track,
                            isDragging = isDragging,
                            dragOffsetY = dragOffsetY,
                            onClick = { onTrackClick(track) },
                            onRemove = { onRemoveFromQueue(track) },
                            dragHandleModifier = dragHandleModifier
                        )
                    }
                }

                // If userQueue was displayed above, show remaining context tracks below
                if (hasUserQueue && queue.contextQueue.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(
                                com.vibe.core.ui.R.string.queue_next_from,
                                queue.contextName ?: ""
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(queue.contextQueue, key = { "context_${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            showArtwork = true,
                            onClick = { onTrackClick(track) }
                        )
                    }
                }

                // Recent history
                if (queue.recentHistory.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(com.vibe.core.ui.R.string.queue_history),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(queue.recentHistory) { record ->
                        TrackRow(
                            track = record.track,
                            showArtwork = true,
                            onClick = { onTrackClick(record.track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QueueItemRow(
    track: Track,
    isDragging: Boolean,
    dragOffsetY: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        tonalElevation = if (isDragging) 8.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 1f)
            .graphicsLayer {
                if (isDragging) {
                    translationY = dragOffsetY
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art thumbnail
            val coverArt = track.album.imageUrl
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
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls: Remove button & Drag Handle
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(com.vibe.core.ui.R.string.queue_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = dragHandleModifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(com.vibe.core.ui.R.string.queue_drag_to_reorder),
                        tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
