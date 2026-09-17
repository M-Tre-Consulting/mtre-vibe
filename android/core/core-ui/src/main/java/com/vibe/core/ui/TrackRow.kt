package com.vibe.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibe.core.model.Track
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrackRow(
    track: Track,
    isSelected: Boolean = false,
    isCompact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectionBg = if (isSelected) {
        // Translucent neutral highlight, without a row outline
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(selectionBg)
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
                    color = if (track.isPlayable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "  ·  ",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
                Text(
                    text = track.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
            }
        } else {
            // Standard two-line layout
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (track.isPlayable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
