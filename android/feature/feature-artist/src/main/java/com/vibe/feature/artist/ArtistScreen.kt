package com.vibe.feature.artist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibe.core.model.Artist
import com.vibe.core.model.Track
import com.vibe.core.ui.TrackRow

@Composable
fun ArtistScreen(
    artist: Artist,
    modifier: Modifier = Modifier,
    onTrackClick: (Track) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onFollowClick: () -> Unit = {}
) {
    var discographyFilter by remember { mutableStateOf("Albums") }
    val filterOptions = listOf("Albums", "EPs & Singles", "Compilations")

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(artist.name, style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${artist.monthlyListeners} monthly listeners", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onFollowClick) {
                    Text(if (artist.isFollowed) "Following" else "Follow")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text("Popular Songs", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(artist.topTracks) { track ->
            TrackRow(
                track = track,
                onClick = { onTrackClick(track) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Discography", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                filterOptions.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = discographyFilter == label,
                        onClick = { discographyFilter = label },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = filterOptions.size)
                    ) {
                        Text(label)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            val displayedAlbums = when (discographyFilter) {
                "Albums" -> artist.albums
                "EPs & Singles" -> artist.singlesAndEps
                else -> artist.compilations
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(displayedAlbums) { album ->
                    ElevatedCard(
                        modifier = Modifier.size(width = 130.dp, height = 170.dp),
                        onClick = { onAlbumClick(album.id) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            Text(album.releaseDate ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
