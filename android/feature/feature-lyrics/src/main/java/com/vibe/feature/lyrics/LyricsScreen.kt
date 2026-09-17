package com.vibe.feature.lyrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibe.core.model.Lyrics

@Composable
fun LyricsScreen(
    lyrics: Lyrics?,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.lyrics_title),
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.lyrics_close)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (lyrics == null || lyrics.lines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.lyrics_not_available),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (lyrics.isSynced) {
            // Find active line index
            val activeIndex = lyrics.lines.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)

            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0) {
                    listState.animateScrollToItem(activeIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isActive = index == activeIndex
                    Text(
                        text = line.words,
                        fontSize = if (isActive) 24.sp else 18.sp,
                        style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            // Unsynced lyrics fallback
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(lyrics.lines) { _, line ->
                    Text(
                        text = line.words,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
