package com.vibe.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onPlaylistClick: (String) -> Unit = {},
    onTrackClick: (String) -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.home_made_for_you),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(5) { index ->
                    ElevatedCard(
                        modifier = Modifier.size(width = 140.dp, height = 180.dp),
                        onClick = { onPlaylistClick("playlist_$index") }
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.home_mix_prefix, index + 1),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.home_recently_played),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(5) { index ->
                    ElevatedCard(
                        modifier = Modifier.size(width = 140.dp, height = 180.dp),
                        onClick = { onTrackClick("track_$index") }
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.home_recent_prefix, index + 1),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
