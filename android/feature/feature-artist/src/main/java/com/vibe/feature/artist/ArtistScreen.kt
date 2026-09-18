package com.vibe.feature.artist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vibe.core.model.Artist
import com.vibe.core.model.Track
import com.vibe.core.ui.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artist: Artist,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    currentTrackId: String? = null,
    onBack: () -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onFollowClick: () -> Unit = {},
    onSwipeToQueue: ((Track) -> Unit)? = null,
    onTrackOptions: ((Track) -> Unit)? = null
) {
    var selectedDiscographyIndex by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = artist.name,
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
                    if (!artist.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artist.imageUrl,
                            contentDescription = artist.name,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            com.vibe.core.ui.R.string.artist_monthly_listeners,
                            artist.monthlyListeners
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onFollowClick) {
                        Text(
                            text = if (artist.isFollowed) androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.artist_following)
                            else androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.artist_follow)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.artist_popular_songs),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(artist.topTracks, key = { it.id }) { track ->
                val isCurrent = track.id == currentTrackId
                TrackRow(
                    track = track,
                    isCurrentTrack = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    showArtwork = true,
                    onClick = { onTrackClick(track) },
                    onLongClick = { onTrackOptions?.invoke(track) },
                    onSwipeToQueue = onSwipeToQueue?.let { cb -> { cb(track) } }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.artist_discography),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val filterResIds = listOf(
                    com.vibe.core.ui.R.string.artist_discography_albums,
                    com.vibe.core.ui.R.string.artist_discography_eps_singles,
                    com.vibe.core.ui.R.string.artist_discography_compilations
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    filterResIds.forEachIndexed { index, resId ->
                        val label = androidx.compose.ui.res.stringResource(resId)
                        SegmentedButton(
                            selected = selectedDiscographyIndex == index,
                            onClick = { selectedDiscographyIndex = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = filterResIds.size)
                        ) {
                            Text(label)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                val displayedAlbums = when (selectedDiscographyIndex) {
                    0 -> artist.albums
                    1 -> artist.singlesAndEps
                    else -> artist.compilations
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(displayedAlbums) { album ->
                        ElevatedCard(
                            modifier = Modifier.size(width = 140.dp, height = 180.dp),
                            shape = RoundedCornerShape(12.dp),
                            onClick = { onAlbumClick(album.id) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = album.releaseDate ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
