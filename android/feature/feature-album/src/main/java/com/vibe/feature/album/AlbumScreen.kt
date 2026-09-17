package com.vibe.feature.album

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vibe.core.model.Album
import com.vibe.core.model.AlbumType
import com.vibe.core.model.Track
import com.vibe.core.ui.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    album: Album,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onTrackClick: (Track, Int) -> Unit = { _, _ -> }
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!album.coverImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = album.coverImageUrl,
                            contentDescription = album.name,
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val typeLabel = when (album.albumType) {
                        AlbumType.EP -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_ep)
                        AlbumType.SINGLE -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_single)
                        AlbumType.COMPILATION -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_compilation)
                        AlbumType.ALBUM -> androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.album_type_album)
                    }
                    Text(
                        text = "${album.artists.joinToString(", ") { it.name }} • $typeLabel • ${album.releaseDate.take(4)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
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
}
