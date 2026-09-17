package com.vibe.feature.queue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibe.core.model.Queue
import com.vibe.core.model.Track
import com.vibe.core.ui.TrackRow

@Composable
fun QueueScreen(
    queue: Queue,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onTrackClick: (Track) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.queue_title),
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.queue_close)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Currently Playing
        queue.currentlyPlaying?.let { current ->
            Text(
                androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.player_now_playing),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            queue.contextName?.let {
                Text(
                    androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.queue_from, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TrackRow(track = current, onClick = { onTrackClick(current) })
            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // User-added queue (inserted before context tracks)
            if (queue.userQueue.isNotEmpty()) {
                item {
                    Text(
                        androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.queue_next),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(queue.userQueue) { track ->
                    TrackRow(track = track, onClick = { onTrackClick(track) })
                }
            }

            // Context tracks (from current album/playlist)
            if (queue.contextQueue.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.vibe.core.ui.R.string.queue_next_from,
                            queue.contextName ?: ""
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(queue.contextQueue) { track ->
                    TrackRow(track = track, onClick = { onTrackClick(track) })
                }
            }

            // Recent history (short-song repeats separate)
            if (queue.recentHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.queue_history),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(queue.recentHistory) { record ->
                    TrackRow(track = record.track, onClick = { onTrackClick(record.track) })
                }
            }
        }
    }
}
