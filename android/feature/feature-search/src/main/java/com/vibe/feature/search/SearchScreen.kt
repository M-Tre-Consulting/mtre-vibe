package com.vibe.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vibe.core.model.AlbumSummary
import com.vibe.core.model.ArtistSummary
import com.vibe.core.model.Playlist
import com.vibe.core.model.Track
import com.vibe.core.network.DualSearchManager
import com.vibe.core.network.SearchResult
import com.vibe.core.ui.TrackRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val result: SearchResult) : SearchUiState
    data class Empty(val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchManager: DualSearchManager,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    currentTrackId: String? = null,
    onTrackClick: (Track, List<Track>) -> Unit = { _, _ -> },
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onSwipeToQueue: ((Track) -> Unit)? = null,
    onTrackOptions: ((Track) -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var searchUiState by remember { mutableStateOf<SearchUiState>(SearchUiState.Idle) }
    val coroutineScope = rememberCoroutineScope()

    val categoryResIds = listOf(
        com.vibe.core.ui.R.string.search_tab_all,
        com.vibe.core.ui.R.string.search_tab_songs,
        com.vibe.core.ui.R.string.search_tab_artists,
        com.vibe.core.ui.R.string.search_tab_albums,
        com.vibe.core.ui.R.string.search_tab_playlists
    )

    fun performSearch(queryToSearch: String) {
        val trimmed = queryToSearch.trim()
        if (trimmed.isEmpty()) {
            searchUiState = SearchUiState.Idle
            return
        }
        coroutineScope.launch {
            searchUiState = SearchUiState.Loading
            try {
                val result = searchManager.executeSearch(trimmed)
                val isEmpty = result.tracks.isEmpty() &&
                        result.artists.isEmpty() &&
                        result.albums.isEmpty() &&
                        result.playlists.isEmpty()
                searchUiState = if (isEmpty) SearchUiState.Empty(trimmed) else SearchUiState.Success(result)
            } catch (e: Exception) {
                searchUiState = SearchUiState.Error(e.message ?: "Search failed")
            }
        }
    }

    // Debounced search when user types
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchUiState = SearchUiState.Idle
            return@LaunchedEffect
        }
        delay(350)
        performSearch(trimmed)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onSearch = { performSearch(query) },
            active = false,
            onActiveChange = {},
            placeholder = { Text(stringResource(com.vibe.core.ui.R.string.search_placeholder)) },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(com.vibe.core.ui.R.string.nav_search)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        searchUiState = SearchUiState.Idle
                    }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(com.vibe.core.ui.R.string.search_clear)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {}

        Spacer(modifier = Modifier.height(10.dp))

        PrimaryScrollableTabRow(
            selectedTabIndex = selectedCategoryIndex,
            edgePadding = 0.dp
        ) {
            categoryResIds.forEachIndexed { index, resId ->
                Tab(
                    selected = selectedCategoryIndex == index,
                    onClick = { selectedCategoryIndex = index },
                    text = { Text(stringResource(resId)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (val state = searchUiState) {
                is SearchUiState.Idle -> {
                    SearchIdleView()
                }

                is SearchUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is SearchUiState.Empty -> {
                    SearchEmptyView(query = state.query)
                }

                is SearchUiState.Error -> {
                    SearchErrorView(
                        message = state.message,
                        onRetry = { performSearch(query) }
                    )
                }

                is SearchUiState.Success -> {
                    SearchResultsView(
                        result = state.result,
                        selectedTab = selectedCategoryIndex,
                        isPlaying = isPlaying,
                        currentTrackId = currentTrackId,
                        onTrackClick = onTrackClick,
                        onArtistClick = onArtistClick,
                        onAlbumClick = onAlbumClick,
                        onPlaylistClick = onPlaylistClick,
                        onSwipeToQueue = onSwipeToQueue,
                        onTrackOptions = onTrackOptions
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchIdleView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(com.vibe.core.ui.R.string.search_idle_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchEmptyView(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(com.vibe.core.ui.R.string.search_no_results, query),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(com.vibe.core.ui.R.string.search_error, message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(com.vibe.core.ui.R.string.retry))
        }
    }
}

@Composable
private fun SearchResultsView(
    result: SearchResult,
    selectedTab: Int,
    isPlaying: Boolean = false,
    currentTrackId: String? = null,
    onTrackClick: (Track, List<Track>) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSwipeToQueue: ((Track) -> Unit)? = null,
    onTrackOptions: ((Track) -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (selectedTab) {
            0 -> {
                // Tab 0: ALL ("Tutto")
                // Top Result (Hero Card)
                val topTrack = result.topResult ?: result.tracks.firstOrNull()
                if (topTrack != null) {
                    item {
                        Text(
                            text = stringResource(com.vibe.core.ui.R.string.search_top_result),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TopResultCard(
                            track = topTrack,
                            onPlayClick = { onTrackClick(topTrack, result.tracks) }
                        )
                    }
                }

                // Songs Section
                if (result.tracks.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(com.vibe.core.ui.R.string.search_tab_songs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(result.tracks.take(5), key = { "all_track_${it.id}" }) { track ->
                        val isCurrent = track.id == currentTrackId
                        TrackRow(
                            track = track,
                            isCurrentTrack = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            showArtwork = true,
                            onClick = { onTrackClick(track, result.tracks) },
                            onLongClick = { onTrackOptions?.invoke(track) },
                            onSwipeToQueue = onSwipeToQueue?.let { cb -> { cb(track) } }
                        )
                    }
                }

                // Artists Section
                if (result.artists.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(com.vibe.core.ui.R.string.search_tab_artists),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(result.artists, key = { "all_artist_${it.id}" }) { artist ->
                                ArtistCard(
                                    artist = artist,
                                    onClick = { onArtistClick(artist.id) }
                                )
                            }
                        }
                    }
                }

                // Albums Section
                if (result.albums.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(com.vibe.core.ui.R.string.search_tab_albums),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(result.albums, key = { "all_album_${it.id}" }) { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = { onAlbumClick(album.id) }
                                )
                            }
                        }
                    }
                }

                // Playlists Section
                if (result.playlists.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(com.vibe.core.ui.R.string.search_tab_playlists),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(result.playlists, key = { "all_playlist_${it.id}" }) { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    onClick = { onPlaylistClick(playlist.id) }
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                // Tab 1: SONGS ("Brani")
                items(result.tracks, key = { "track_${it.id}" }) { track ->
                    val isCurrent = track.id == currentTrackId
                    TrackRow(
                        track = track,
                        isCurrentTrack = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        showArtwork = true,
                        onClick = { onTrackClick(track, result.tracks) },
                        onLongClick = { onTrackOptions?.invoke(track) },
                        onSwipeToQueue = onSwipeToQueue?.let { cb -> { cb(track) } }
                    )
                }
            }

            2 -> {
                // Tab 2: ARTISTS ("Artisti")
                items(result.artists, key = { "artist_${it.id}" }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onArtistClick(artist.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!artist.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = artist.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(artist.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(com.vibe.core.ui.R.string.search_badge_artist),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            3 -> {
                // Tab 3: ALBUMS ("Album")
                items(result.albums, key = { "album_${it.id}" }) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlbumClick(album.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!album.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = album.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Album, contentDescription = null)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val year = album.releaseDate?.take(4) ?: ""
                            Text(
                                if (year.isNotEmpty()) "${stringResource(com.vibe.core.ui.R.string.search_badge_album)} • $year"
                                else stringResource(com.vibe.core.ui.R.string.search_badge_album),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            4 -> {
                // Tab 4: PLAYLISTS ("Playlist")
                items(result.playlists, key = { "playlist_${it.id}" }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!playlist.coverImageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = playlist.coverImageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.QueueMusic, contentDescription = null)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "By ${playlist.ownerName} • ${playlist.totalTracks} songs",
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

@Composable
private fun TopResultCard(
    track: Track,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val albumArt = track.album.imageUrl
            if (!albumArt.isNullOrBlank()) {
                AsyncImage(
                    model = albumArt,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(36.dp))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(com.vibe.core.ui.R.string.search_badge_song),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            FilledIconButton(
                onClick = onPlayClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(com.vibe.core.ui.R.string.playlist_play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(
    artist: ArtistSummary,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!artist.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = artist.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(com.vibe.core.ui.R.string.search_badge_artist),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlbumCard(
    album: AlbumSummary,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable { onClick() }
    ) {
        if (!album.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = album.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val year = album.releaseDate?.take(4) ?: ""
        if (year.isNotEmpty()) {
            Text(
                text = year,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable { onClick() }
    ) {
        if (!playlist.coverImageUrl.isNullOrBlank()) {
            AsyncImage(
                model = playlist.coverImageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.ownerName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
