package com.vibe.feature.album

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibe.core.model.Album
import com.vibe.core.model.AlbumType
import com.vibe.core.model.Track
import com.vibe.core.ui.TrackRow

@Composable
fun AlbumScreen(
    album: Album,
    modifier: Modifier = Modifier,
    onTrackClick: (Track, Int) -> Unit = { _, _ -> }
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(album.name, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(4.dp))
                val typeLabel = when (album.albumType) {
                    AlbumType.EP -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_ep)
                    AlbumType.SINGLE -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_single)
                    AlbumType.COMPILATION -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_compilation)
                    AlbumType.ALBUM -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_album)
                }
                Text(
                    "${album.artists.joinToString(", ") { it.name }} • $typeLabel • ${album.releaseDate.take(4)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        itemsIndexed(album.tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                onClick = { onTrackClick(track, index) }
            )
        }
    }
}
