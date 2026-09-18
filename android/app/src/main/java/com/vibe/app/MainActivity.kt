package com.vibe.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.vibe.core.connect.ConnectDeviceManager
import com.vibe.core.model.Album
import com.vibe.core.model.AlbumSummary
import com.vibe.core.model.Artist
import com.vibe.core.model.DeviceType
import com.vibe.core.model.Lyrics
import com.vibe.core.model.Playlist
import com.vibe.core.model.Queue
import com.vibe.core.model.RepeatMode
import com.vibe.core.model.Track
import com.vibe.core.model.isCurrentDevice
import com.vibe.core.model.isRemote
import com.vibe.core.network.DualSearchManager
import com.vibe.core.network.SpotifyApiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.vibe.core.network.model.UserProfileDto
import com.vibe.feature.lyrics.LyricsScreen
import com.vibe.feature.queue.QueueScreen
import com.vibe.core.network.auth.AuthState
import com.vibe.core.network.auth.SpotifyAuthConfig
import com.vibe.core.network.auth.SpotifyAuthManager
import com.vibe.core.model.PlaybackMode
import com.vibe.core.model.VibeSettings
import com.vibe.core.playback.RoutingAudioPlayerImpl
import com.vibe.core.playback.SettingsManager
import com.vibe.core.playback.SpotifyAppRemoteManager
import com.vibe.core.playback.VibeAudioPlayer
import com.vibe.core.ui.ExpressiveNavItem
import com.vibe.core.ui.ExpressivePillNavBar
import com.vibe.core.ui.SwipeBackContainer
import com.vibe.core.ui.SwipeDismissContainer
import com.vibe.core.ui.TrackOptionsBottomSheet
import com.vibe.core.ui.VibeTheme
import com.vibe.feature.album.AlbumScreen
import com.vibe.feature.artist.ArtistScreen
import com.vibe.feature.devices.DevicesDialog
import com.vibe.feature.devices.SettingsDialog
import com.vibe.feature.home.HomeScreen
import com.vibe.feature.library.LibraryScreen
import com.vibe.feature.player.FullPlayerScreen
import com.vibe.feature.player.MiniPlayerBar
import com.vibe.feature.playlist.PlaylistScreen
import com.vibe.feature.search.SearchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val authManager: SpotifyAuthManager by inject()
    private val audioPlayer: VibeAudioPlayer by inject()
    private val spotifyAppRemoteManager: SpotifyAppRemoteManager by inject()
    private val apiService: SpotifyApiService by inject()
    private val connectDeviceManager: ConnectDeviceManager by inject()
    private val searchManager: DualSearchManager by inject()
    private val settingsManager: SettingsManager by inject()

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        spotifyAppRemoteManager.setActivity(this)
        handleIncomingIntent(intent)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val authState by authManager.authState.collectAsState(initial = AuthState.Loading)
            val playbackState by audioPlayer.playbackState.collectAsState()
            val devices by connectDeviceManager.devices.collectAsState()
            val vibeSettings by settingsManager.settingsFlow.collectAsState(initial = VibeSettings())

            var currentScreen by remember { mutableStateOf("home") }
            var isFullPlayerVisible by remember { mutableStateOf(false) }
            var isDevicesDialogVisible by remember { mutableStateOf(false) }
            var isRefreshingDevices by remember { mutableStateOf(false) }
            var isSettingsDialogVisible by remember { mutableStateOf(false) }
            var isQueueVisible by remember { mutableStateOf(false) }
            var isLyricsVisible by remember { mutableStateOf(false) }

            var userProfile by remember { mutableStateOf<UserProfileDto?>(null) }
            var currentQueue by remember { mutableStateOf<Queue?>(null) }
            var currentLyrics by remember { mutableStateOf<Lyrics?>(null) }
            var isLoadingLyrics by remember { mutableStateOf(false) }

            var activeArtist by remember { mutableStateOf<Artist?>(null) }
            var activeAlbum by remember { mutableStateOf<Album?>(null) }
            var activePlaylist by remember { mutableStateOf<Playlist?>(null) }
            var userPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
            var userLikedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
            var userSavedAlbums by remember { mutableStateOf<List<AlbumSummary>>(emptyList()) }
            var selectedTrackForOptions by remember { mutableStateOf<Track?>(null) }
            var currentAlertMessage by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                audioPlayer.errorEvents.collect { errorMsg ->
                    currentAlertMessage = errorMsg
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }

            LaunchedEffect(currentAlertMessage) {
                if (currentAlertMessage != null) {
                    delay(8000)
                    currentAlertMessage = null
                }
            }

            LaunchedEffect(authState) {
                if (authState is AuthState.Authenticated) {
                    apiService.getCurrentUserProfile().onSuccess { profile ->
                        userProfile = profile
                    }
                    apiService.getCurrentUserPlaylists().onSuccess { pls ->
                        userPlaylists = pls
                    }
                    apiService.getLikedSongs(0, 50).onSuccess { tracks ->
                        userLikedTracks = tracks
                    }
                    apiService.getUserSavedAlbums(50, 0).onSuccess { albums ->
                        userSavedAlbums = albums
                    }
                    refreshDevicesAndSyncPlayback(autoSwitchIfRemoteActive = true)

                    // Periodic background check to detect remote playback starting on PC/Speaker
                    while (isActive) {
                        delay(8000)
                        refreshDevicesAndSyncPlayback(autoSwitchIfRemoteActive = true)
                    }
                }
            }

            LaunchedEffect(isDevicesDialogVisible) {
                if (isDevicesDialogVisible) {
                    isRefreshingDevices = true
                    refreshDevicesAndSyncPlayback(autoSwitchIfRemoteActive = false)
                    isRefreshingDevices = false
                    while (isActive) {
                        delay(2500)
                        refreshDevicesAndSyncPlayback(autoSwitchIfRemoteActive = false)
                    }
                }
            }

            LaunchedEffect(isQueueVisible, playbackState.currentTrack) {
                if (isQueueVisible) {
                    apiService.getUserQueue().onSuccess { q ->
                        currentQueue = q
                    }
                }
            }

            LaunchedEffect(isLyricsVisible, playbackState.currentTrack?.id) {
                if (isLyricsVisible) {
                    val currentTrack = playbackState.currentTrack
                    if (currentTrack != null) {
                        isLoadingLyrics = true
                        currentLyrics = null
                        val lyricsResult = apiService.getLyrics(
                            trackId = currentTrack.id,
                            trackName = currentTrack.name,
                            artistName = currentTrack.artists.firstOrNull()?.name,
                            durationSec = (currentTrack.durationMs / 1000).toInt()
                        )
                        currentLyrics = lyricsResult.getOrNull()
                        isLoadingLyrics = false
                    } else {
                        currentLyrics = null
                        isLoadingLyrics = false
                    }
                }
            }

            VibeTheme(isAlbumArtTintEnabled = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (authState) {
                        is AuthState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is AuthState.Unauthenticated -> {
                            val savedClientId by authManager.savedClientId.collectAsState(initial = null)
                            val defaultClientId = BuildConfig.SPOTIFY_CLIENT_ID.ifBlank { "" }
                            LoginScreen(
                                initialClientId = savedClientId ?: defaultClientId,
                                onLoginClick = { enteredClientId ->
                                    lifecycleScope.launch {
                                        try {
                                            authManager.launchLogin(this@MainActivity, customClientId = enteredClientId)
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, e.message ?: "Invalid Client ID", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                        is AuthState.Authenticated -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Scaffold(
                                bottomBar = {
                                    val isAnyModalVisible = isFullPlayerVisible || isQueueVisible || isLyricsVisible || isSettingsDialogVisible || isDevicesDialogVisible || selectedTrackForOptions != null
                                    AnimatedVisibility(
                                        visible = !isAnyModalVisible,
                                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .navigationBarsPadding(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // Mini Player Bar docked above Navigation Bar
                                            AnimatedVisibility(
                                                visible = playbackState.currentTrack != null,
                                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                            ) {
                                                MiniPlayerBar(
                                                    playbackState = playbackState,
                                                    onPlayPause = {
                                                        if (playbackState.isPlaying) audioPlayer.pause()
                                                        else audioPlayer.resume()
                                                    },
                                                    onSkipNext = { audioPlayer.skipToNext() },
                                                    onBarClick = { isFullPlayerVisible = true },
                                                    onArtistClick = {
                                                        playbackState.currentTrack?.artists?.firstOrNull()?.id?.let { artistId ->
                                                            lifecycleScope.launch {
                                                                apiService.getArtist(artistId).onSuccess { activeArtist = it }
                                                            }
                                                        }
                                                    }
                                                )
                                            }

                                            // Material 3 Expressive Floating Pill Navbar
                                            ExpressivePillNavBar(
                                                items = listOf(
                                                    ExpressiveNavItem(
                                                        id = "home",
                                                        label = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_home),
                                                        icon = Icons.Default.Home
                                                    ),
                                                    ExpressiveNavItem(
                                                        id = "search",
                                                        label = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_search),
                                                        icon = Icons.Default.Search
                                                    ),
                                                    ExpressiveNavItem(
                                                        id = "library",
                                                        label = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_library),
                                                        icon = Icons.Default.LibraryMusic
                                                    )
                                                ),
                                                selectedItemId = if (activeArtist == null && activeAlbum == null && activePlaylist == null) currentScreen else "",
                                                onItemSelected = { selected ->
                                                    activeArtist = null
                                                    activeAlbum = null
                                                    activePlaylist = null
                                                    currentScreen = selected
                                                }
                                            )
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                val currentDestination = when {
                                    activeArtist != null -> "artist"
                                    activeAlbum != null -> "album"
                                    activePlaylist != null -> "playlist"
                                    else -> currentScreen
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    AnimatedContent(
                                        targetState = currentDestination,
                                        transitionSpec = {
                                            val isEnteringDetail = targetState in listOf("playlist", "album", "artist") &&
                                                    initialState !in listOf("playlist", "album", "artist")
                                            val isExitingDetail = initialState in listOf("playlist", "album", "artist") &&
                                                    targetState !in listOf("playlist", "album", "artist")

                                            if (isEnteringDetail) {
                                                (slideInHorizontally(
                                                    initialOffsetX = { it / 2 },
                                                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                                ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.94f))
                                                    .togetherWith(
                                                        slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
                                                    )
                                            } else if (isExitingDetail) {
                                                (slideInHorizontally(
                                                    initialOffsetX = { -it / 4 },
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                ) + fadeIn() + scaleIn(initialScale = 0.96f))
                                                    .togetherWith(
                                                        slideOutHorizontally(
                                                            targetOffsetX = { it },
                                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                        ) + fadeOut()
                                                    )
                                            } else {
                                                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                                    .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                                            }
                                        },
                                        label = "navigationTransition"
                                    ) { dest ->
                                        when (dest) {
                                            "artist" -> {
                                                activeArtist?.let { artist ->
                                                    SwipeBackContainer(onBack = { activeArtist = null }) {
                                                        ArtistScreen(
                                                            artist = artist,
                                                            isPlaying = playbackState.isPlaying,
                                                            currentTrackId = playbackState.currentTrack?.id,
                                                            onBack = { activeArtist = null },
                                                            onTrackClick = { track ->
                                                                playLocalTrack(track, artist.topTracks)
                                                            },
                                                            onAlbumClick = { albumId ->
                                                                lifecycleScope.launch {
                                                                    apiService.getAlbum(albumId).onSuccess { activeAlbum = it }
                                                                }
                                                            },
                                                            onSwipeToQueue = { track -> addTrackToQueue(track) },
                                                            onTrackOptions = { track -> selectedTrackForOptions = track }
                                                        )
                                                    }
                                                    BackHandler { activeArtist = null }
                                                }
                                            }
                                            "album" -> {
                                                activeAlbum?.let { album ->
                                                    SwipeBackContainer(onBack = { activeAlbum = null }) {
                                                        AlbumScreen(
                                                            album = album,
                                                            isPlaying = playbackState.isPlaying && album.tracks.any { it.id == playbackState.currentTrack?.id },
                                                            currentTrackId = playbackState.currentTrack?.id,
                                                            isShuffleActive = playbackState.shuffleEnabled,
                                                            isSmartShuffleActive = playbackState.isSmartShuffleActive,
                                                            repeatMode = playbackState.repeatMode,
                                                            onBack = { activeAlbum = null },
                                                            onArtistClick = { artistId ->
                                                                activeAlbum = null
                                                                lifecycleScope.launch {
                                                                    apiService.getArtist(artistId).onSuccess { activeArtist = it }
                                                                }
                                                            },
                                                            onPlayClick = {
                                                                if (playbackState.isPlaying) {
                                                                    audioPlayer.pause()
                                                                } else if (album.tracks.isNotEmpty()) {
                                                                    if (playbackState.currentTrack != null && album.tracks.any { it.id == playbackState.currentTrack?.id }) {
                                                                        audioPlayer.resume()
                                                                    } else {
                                                                        playLocalCollection(album.tracks, 0)
                                                                    }
                                                                }
                                                            },
                                                            onShuffleClick = {
                                                                audioPlayer.toggleShuffle()
                                                                if (!playbackState.isPlaying && album.tracks.isNotEmpty()) {
                                                                    playLocalCollection(album.tracks, 0)
                                                                }
                                                            },
                                                            onSmartShuffleClick = {
                                                                audioPlayer.toggleSmartShuffle()
                                                                if (!playbackState.isPlaying && album.tracks.isNotEmpty()) {
                                                                    playLocalCollection(album.tracks, 0)
                                                                }
                                                            },
                                                            onRepeatClick = {
                                                                audioPlayer.toggleRepeat()
                                                            },
                                                            onTrackClick = { track, _ ->
                                                                playLocalTrack(track, album.tracks)
                                                            },
                                                            onSwipeToQueue = { track -> addTrackToQueue(track) },
                                                            onTrackOptions = { track -> selectedTrackForOptions = track }
                                                        )
                                                    }
                                                    BackHandler { activeAlbum = null }
                                                }
                                            }
                                            "playlist" -> {
                                                activePlaylist?.let { playlist ->
                                                    SwipeBackContainer(onBack = { activePlaylist = null }) {
                                                        PlaylistScreen(
                                                            playlist = playlist,
                                                            isPlaying = playbackState.isPlaying && playlist.tracks.any { it.id == playbackState.currentTrack?.id },
                                                            currentTrackId = playbackState.currentTrack?.id,
                                                            isShuffleActive = playbackState.shuffleEnabled,
                                                            isSmartShuffleActive = playbackState.isSmartShuffleActive,
                                                            repeatMode = playbackState.repeatMode,
                                                            onBack = { activePlaylist = null },
                                                            onPlayClick = {
                                                                if (playbackState.isPlaying) {
                                                                    audioPlayer.pause()
                                                                } else if (playlist.tracks.isNotEmpty()) {
                                                                    if (playbackState.currentTrack != null && playlist.tracks.any { it.id == playbackState.currentTrack?.id }) {
                                                                        audioPlayer.resume()
                                                                    } else {
                                                                        playLocalCollection(playlist.tracks, 0)
                                                                    }
                                                                }
                                                            },
                                                            onShuffleClick = {
                                                                audioPlayer.toggleShuffle()
                                                                if (!playbackState.isPlaying && playlist.tracks.isNotEmpty()) {
                                                                    playLocalCollection(playlist.tracks, 0)
                                                                }
                                                            },
                                                            onSmartShuffleClick = {
                                                                audioPlayer.toggleSmartShuffle()
                                                                if (!playbackState.isPlaying && playlist.tracks.isNotEmpty()) {
                                                                    playLocalCollection(playlist.tracks, 0)
                                                                }
                                                            },
                                                            onRepeatClick = {
                                                                audioPlayer.toggleRepeat()
                                                            },
                                                            onTrackClick = { track, _ ->
                                                                playLocalTrack(track, playlist.tracks)
                                                            },
                                                            onSwipeToQueue = { track -> addTrackToQueue(track) },
                                                            onTrackOptions = { track -> selectedTrackForOptions = track }
                                                        )
                                                    }
                                                    BackHandler { activePlaylist = null }
                                                }
                                            }
                                            "home" -> HomeScreen(
                                                playlists = userPlaylists,
                                                recentTracks = userLikedTracks,
                                                userAvatarUrl = userProfile?.images?.firstOrNull()?.url,
                                                currentTrackId = playbackState.currentTrack?.id,
                                                isPlaying = playbackState.isPlaying,
                                                onPlaylistClick = { playlistId ->
                                                    lifecycleScope.launch {
                                                        apiService.getPlaylist(playlistId).onSuccess { p ->
                                                            activePlaylist = p
                                                        }
                                                    }
                                                },
                                                onTrackClick = { track, tracks ->
                                                    playLocalTrack(track, tracks)
                                                },
                                                onDevicesClick = { isDevicesDialogVisible = true },
                                                onSettingsClick = { isSettingsDialogVisible = true }
                                            )
                                            "search" -> SearchScreen(
                                                searchManager = searchManager,
                                                isPlaying = playbackState.isPlaying,
                                                currentTrackId = playbackState.currentTrack?.id,
                                                onTrackClick = { track, contextTracks ->
                                                    playLocalTrack(track, contextTracks)
                                                },
                                                onArtistClick = { artistId ->
                                                    lifecycleScope.launch {
                                                        apiService.getArtist(artistId).onSuccess { activeArtist = it }
                                                    }
                                                },
                                                onAlbumClick = { albumId ->
                                                    lifecycleScope.launch {
                                                        apiService.getAlbum(albumId).onSuccess { activeAlbum = it }
                                                    }
                                                },
                                                onPlaylistClick = { playlistId ->
                                                    lifecycleScope.launch {
                                                        apiService.getPlaylist(playlistId).onSuccess { activePlaylist = it }
                                                    }
                                                },
                                                onSwipeToQueue = { track -> addTrackToQueue(track) },
                                                onTrackOptions = { track -> selectedTrackForOptions = track }
                                            )
                                            "library" -> LibraryScreen(
                                                playlists = userPlaylists,
                                                albums = userSavedAlbums,
                                                likedTracks = userLikedTracks,
                                                userAvatarUrl = userProfile?.images?.firstOrNull()?.url,
                                                isPlaying = playbackState.isPlaying,
                                                currentTrackId = playbackState.currentTrack?.id,
                                                onDevicesClick = { isDevicesDialogVisible = true },
                                                onSettingsClick = { isSettingsDialogVisible = true },
                                                onOpenLikedSongs = {
                                                    activePlaylist = Playlist(
                                                        id = "liked_songs",
                                                        uri = "spotify:user:liked",
                                                        name = "Brani che ti piacciono",
                                                        description = "I brani salvati nella tua libreria",
                                                        ownerName = "Tu",
                                                        ownerId = "me",
                                                        tracks = userLikedTracks,
                                                        totalTracks = userLikedTracks.size,
                                                        isPinned = true,
                                                        coverImageUrl = null
                                                    )
                                                },
                                                onPlaylistClick = { playlist ->
                                                    lifecycleScope.launch {
                                                        apiService.getPlaylist(playlist.id).onSuccess { activePlaylist = it }
                                                    }
                                                },
                                                onPlaylistDoubleClick = { playlist ->
                                                    lifecycleScope.launch {
                                                        apiService.getPlaylist(playlist.id).onSuccess { p ->
                                                            if (p.tracks.isNotEmpty()) {
                                                                playLocalTrack(p.tracks.first(), p.tracks)
                                                            }
                                                        }
                                                    }
                                                },
                                                onAlbumClick = { albumId ->
                                                    lifecycleScope.launch {
                                                        apiService.getAlbum(albumId).onSuccess { activeAlbum = it }
                                                    }
                                                },
                                                onTrackClick = { track, tracks ->
                                                    playLocalTrack(track, tracks)
                                                },
                                                onShuffleAll = {
                                                    if (userLikedTracks.isNotEmpty()) {
                                                        audioPlayer.setShuffle(true)
                                                        playLocalCollection(userLikedTracks, 0)
                                                    } else if (userPlaylists.isNotEmpty()) {
                                                        lifecycleScope.launch {
                                                            apiService.getPlaylist(userPlaylists.random().id).onSuccess { p ->
                                                                if (p.tracks.isNotEmpty()) {
                                                                    audioPlayer.setShuffle(true)
                                                                    playLocalCollection(p.tracks, 0)
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                onSwipeToQueue = { track -> addTrackToQueue(track) },
                                                onTrackOptions = { track -> selectedTrackForOptions = track }
                                            )
                                        }
                                     }
                                 }
                             }

                            // Full Player Screen Modal with expressive spring slide animations
                            AnimatedVisibility(
                                visible = isFullPlayerVisible,
                                enter = slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(250)),
                                exit = slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                            ) {
                                BackHandler { isFullPlayerVisible = false }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
                                    SwipeDismissContainer(onDismiss = { isFullPlayerVisible = false }) {
                                        FullPlayerScreen(
                                            playbackState = playbackState,
                                            onPlayPause = {
                                                if (playbackState.isPlaying) audioPlayer.pause()
                                                else audioPlayer.resume()
                                            },
                                            onSkipNext = { audioPlayer.skipToNext() },
                                            onSkipPrevious = { audioPlayer.skipToPrevious() },
                                            onSeekTo = { pos -> audioPlayer.seekTo(pos) },
                                            onClose = { isFullPlayerVisible = false },
                                            onOpenDevices = { isDevicesDialogVisible = true },
                                            onOpenQueue = {
                                                isQueueVisible = true
                                                lifecycleScope.launch {
                                                    apiService.getUserQueue().onSuccess { q ->
                                                        currentQueue = q
                                                    }
                                                }
                                            },
                                            onArtistClick = { artistId ->
                                                isFullPlayerVisible = false
                                                lifecycleScope.launch {
                                                    apiService.getArtist(artistId).onSuccess { activeArtist = it }
                                                }
                                            },
                                            onOpenLyrics = { isLyricsVisible = true },
                                            onToggleShuffle = { audioPlayer.setShuffle(!playbackState.shuffleEnabled) },
                                            onToggleRepeat = {
                                                val next = when (playbackState.repeatMode) {
                                                    RepeatMode.OFF -> RepeatMode.ALL
                                                    RepeatMode.ALL -> RepeatMode.ONE
                                                    RepeatMode.ONE -> RepeatMode.OFF
                                                }
                                                audioPlayer.setRepeatMode(next)
                                            }
                                        )
                                    }
                                }
                            }

                            // Lyrics Screen Modal
                            AnimatedVisibility(
                                visible = isLyricsVisible,
                                enter = slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(250)),
                                exit = slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                            ) {
                                BackHandler { isLyricsVisible = false }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .navigationBarsPadding(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    LyricsScreen(
                                        lyrics = currentLyrics,
                                        currentPositionMs = playbackState.positionMs,
                                        isLoading = isLoadingLyrics,
                                        trackName = playbackState.currentTrack?.name,
                                        artistName = playbackState.currentTrack?.artists?.firstOrNull()?.name,
                                        onSeekTo = { posMs ->
                                            audioPlayer.seekTo(posMs)
                                        },
                                        onClose = { isLyricsVisible = false }
                                    )
                                }
                            }

                            // Queue Screen Modal
                            AnimatedVisibility(
                                visible = isQueueVisible,
                                enter = slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(250)),
                                exit = slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                            ) {
                                BackHandler { isQueueVisible = false }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .navigationBarsPadding(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    val queueToDisplay = currentQueue ?: Queue(
                                        currentlyPlaying = playbackState.currentTrack,
                                        contextQueue = activePlaylist?.tracks ?: activeAlbum?.tracks ?: emptyList()
                                    )
                                    QueueScreen(
                                        queue = queueToDisplay,
                                        onClose = { isQueueVisible = false },
                                        onTrackClick = { track ->
                                            val upcomingList = if (queueToDisplay.userQueue.isNotEmpty()) queueToDisplay.userQueue else queueToDisplay.contextQueue
                                            val remaining = upcomingList.dropWhile { it.id != track.id }
                                            currentQueue = queueToDisplay.copy(
                                                userQueue = queueToDisplay.userQueue.filterNot { it.id == track.id },
                                                contextQueue = queueToDisplay.contextQueue.filterNot { it.id == track.id }
                                            )
                                            playLocalTrack(track, remaining)
                                        },
                                        onRemoveFromQueue = { track ->
                                            if (queueToDisplay.userQueue.isNotEmpty()) {
                                                currentQueue = queueToDisplay.copy(
                                                    userQueue = queueToDisplay.userQueue.filterNot { it.id == track.id }
                                                )
                                            } else {
                                                currentQueue = queueToDisplay.copy(
                                                    contextQueue = queueToDisplay.contextQueue.filterNot { it.id == track.id }
                                                )
                                            }
                                        },
                                        onMoveQueueItem = { fromIndex, toIndex ->
                                            if (queueToDisplay.userQueue.isNotEmpty()) {
                                                val list = queueToDisplay.userQueue.toMutableList()
                                                if (fromIndex in list.indices && toIndex in list.indices) {
                                                    val item = list.removeAt(fromIndex)
                                                    list.add(toIndex, item)
                                                    currentQueue = queueToDisplay.copy(userQueue = list)
                                                    (audioPlayer as? RoutingAudioPlayerImpl)?.moveQueueItem(fromIndex, toIndex)
                                                }
                                            } else if (queueToDisplay.contextQueue.isNotEmpty()) {
                                                val list = queueToDisplay.contextQueue.toMutableList()
                                                if (fromIndex in list.indices && toIndex in list.indices) {
                                                    val item = list.removeAt(fromIndex)
                                                    list.add(toIndex, item)
                                                    currentQueue = queueToDisplay.copy(contextQueue = list)
                                                    (audioPlayer as? RoutingAudioPlayerImpl)?.moveQueueItem(fromIndex, toIndex)
                                                }
                                            }
                                        },
                                        onClearQueue = {
                                            currentQueue = queueToDisplay.copy(userQueue = emptyList(), contextQueue = emptyList())
                                        }
                                    )
                                }
                            }

                            // Connect Devices Dialog
                            if (isDevicesDialogVisible) {
                                val activeDeviceState = playbackState.activeDevice
                                val anyRemoteActive = devices.any { it.isActive && it.isRemote } ||
                                        (activeDeviceState?.isRemote == true)
                                val routingPlayer = audioPlayer as? RoutingAudioPlayerImpl
                                val isSpotifyInstalled = routingPlayer?.spotifyRemote?.isSpotifyInstalled() ?: false
                                val activeRemoteId = activeDeviceState?.id ?: devices.firstOrNull { it.isActive && it.isRemote }?.id
                                val isLocalActive = (vibeSettings.playbackMode != PlaybackMode.CONNECT) && !anyRemoteActive

                                DevicesDialog(
                                    devices = devices,
                                    isLocalPlaybackActive = isLocalActive,
                                    activeDeviceId = activeRemoteId,
                                    isRefreshing = isRefreshingDevices,
                                    isSpotifyAppInstalled = isSpotifyInstalled,
                                    onSelectLocalPlayback = {
                                        android.util.Log.i("VIBE_CONNECT", "User selected Local Playback on this device")
                                        isDevicesDialogVisible = false
                                        lifecycleScope.launch {
                                            val phoneConnectDevice = devices.firstOrNull { it.isCurrentDevice(devices) }
                                            if (phoneConnectDevice != null) {
                                                settingsManager.setPlaybackMode(PlaybackMode.CONNECT)
                                                routingPlayer?.switchToConnect(phoneConnectDevice.id, transferPlayback = playbackState.isPlaying)
                                                apiService.transferPlayback(phoneConnectDevice.id, play = playbackState.isPlaying)
                                                Toast.makeText(this@MainActivity, "Connesso a ${phoneConnectDevice.name} via Spotify Connect", Toast.LENGTH_SHORT).show()
                                            } else {
                                                settingsManager.setPlaybackMode(PlaybackMode.SPOTIFY_REMOTE)
                                                routingPlayer?.switchToSpotifyRemote(transferPlayback = playbackState.isPlaying)
                                            }
                                            delay(500)
                                            refreshDevicesAndSyncPlayback()
                                        }
                                    },
                                    onSelectDevice = { dev ->
                                        android.util.Log.i("VIBE_CONNECT", "User selected device: '${dev.name}' (ID: ${dev.id}) -> Switching mode to CONNECT")
                                        isDevicesDialogVisible = false
                                        lifecycleScope.launch {
                                            settingsManager.setPlaybackMode(PlaybackMode.CONNECT)
                                            routingPlayer?.switchToConnect(dev.id, transferPlayback = playbackState.isPlaying)
                                            val result = apiService.transferPlayback(dev.id, play = playbackState.isPlaying)
                                            if (result.isSuccess) {
                                                Toast.makeText(this@MainActivity, "Connesso a ${dev.name}", Toast.LENGTH_SHORT).show()
                                            }
                                            delay(500)
                                            refreshDevicesAndSyncPlayback()
                                        }
                                    },
                                    onVolumeChange = { dev, vol ->
                                        android.util.Log.d("VIBE_CONNECT", "Volume changed for '${dev.name}': $vol%")
                                        audioPlayer.setVolume(vol.toFloat() / 100f)
                                    },
                                    onWakeSpotify = {
                                        val launchIntent = packageManager.getLaunchIntentForPackage("com.spotify.music")
                                        if (launchIntent != null) {
                                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            startActivity(launchIntent)
                                            Toast.makeText(this@MainActivity, "Apertura Spotify per attivare Connect...", Toast.LENGTH_SHORT).show()
                                            lifecycleScope.launch {
                                                delay(2500)
                                                refreshDevicesAndSyncPlayback()
                                            }
                                        } else {
                                            Toast.makeText(this@MainActivity, "App Spotify non installata", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onRefresh = {
                                        lifecycleScope.launch {
                                            isRefreshingDevices = true
                                            refreshDevicesAndSyncPlayback()
                                            isRefreshingDevices = false
                                        }
                                    },
                                    onDismiss = { isDevicesDialogVisible = false }
                                )
                            }

                            // Settings Dialog
                            if (isSettingsDialogVisible) {
                                SettingsDialog(
                                    settings = vibeSettings,
                                    onUpdateSettings = { updated ->
                                        lifecycleScope.launch {
                                            settingsManager.setPlaybackMode(updated.playbackMode)
                                            settingsManager.setAutoFallback(updated.autoFallbackEnabled)
                                            settingsManager.setAudioQuality(updated.audioQuality)
                                        }
                                    },
                                    onClearCache = {
                                        lifecycleScope.launch {
                                            val cacheDir = java.io.File(cacheDir, "vibe_audio_cache")
                                            cacheDir.deleteRecursively()
                                            Toast.makeText(this@MainActivity, "Cache svuotata", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onLogout = {
                                        lifecycleScope.launch {
                                            authManager.logout()
                                            isSettingsDialogVisible = false
                                        }
                                    },
                                    onDismiss = { isSettingsDialogVisible = false }
                                )
                            }

                            // Track Context Options Modal Bottom Sheet
                            selectedTrackForOptions?.let { track ->
                                val isLiked = userLikedTracks.any { it.id == track.id }
                                TrackOptionsBottomSheet(
                                    track = track,
                                    isLiked = isLiked,
                                    onDismiss = { selectedTrackForOptions = null },
                                    onPlayNow = {
                                        playLocalTrack(track, listOf(track))
                                    },
                                    onAddToQueue = {
                                        addTrackToQueue(track)
                                    },
                                    onToggleLike = {
                                        val newLiked = !isLiked
                                        lifecycleScope.launch {
                                            apiService.setLiked(track.id, newLiked).onSuccess {
                                                if (newLiked) {
                                                    userLikedTracks = listOf(track.copy(isLiked = true)) + userLikedTracks
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        getString(com.vibe.core.ui.R.string.track_options_add_to_favorites),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    userLikedTracks = userLikedTracks.filterNot { it.id == track.id }
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        getString(com.vibe.core.ui.R.string.track_options_remove_from_favorites),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }.onFailure { err ->
                                                Toast.makeText(this@MainActivity, "Errore: ${err.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onViewAlbum = {
                                        if (track.album.id.isNotBlank()) {
                                            lifecycleScope.launch {
                                                apiService.getAlbum(track.album.id).onSuccess { activeAlbum = it }
                                            }
                                        }
                                    },
                                    onViewArtist = {
                                        val artistId = track.artists.firstOrNull()?.id
                                        if (!artistId.isNullOrBlank()) {
                                            lifecycleScope.launch {
                                                apiService.getArtist(artistId).onSuccess { activeArtist = it }
                                            }
                                        }
                                    },
                                    onShare = {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, track.name)
                                            putExtra(
                                                android.content.Intent.EXTRA_TEXT,
                                                "Ascolta \"${track.name}\" di ${track.artists.firstOrNull()?.name ?: ""} su Spotify: https://open.spotify.com/track/${track.id}"
                                            )
                                        }
                                        startActivity(android.content.Intent.createChooser(shareIntent, track.name))
                                    }
                                )
                            }

                            // Top Error / Alert Banner Overlay (Clear, readable, actionable)
                            AnimatedVisibility(
                                visible = currentAlertMessage != null,
                                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                currentAlertMessage?.let { alertText ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        tonalElevation = 8.dp,
                                        shadowElevation = 10.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .padding(top = 2.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = alertText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { currentAlertMessage = null },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Chiudi",
                                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val spotifyInstalled = (audioPlayer as? RoutingAudioPlayerImpl)?.spotifyRemote?.isSpotifyInstalled() == true
                                                if (spotifyInstalled) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            val launchIntent = packageManager.getLaunchIntentForPackage("com.spotify.music")
                                                            if (launchIntent != null) {
                                                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                startActivity(launchIntent)
                                                            }
                                                            currentAlertMessage = null
                                                        },
                                                        modifier = Modifier.height(36.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                                    ) {
                                                        Text("Apri Spotify", style = MaterialTheme.typography.labelLarge)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Button(
                                                    onClick = {
                                                        isDevicesDialogVisible = true
                                                        currentAlertMessage = null
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.error,
                                                        contentColor = MaterialTheme.colorScheme.onError
                                                    ),
                                                    modifier = Modifier.height(36.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                                ) {
                                                    Text(
                                                        "Dispositivi",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    private fun playLocalTrack(track: Track, contextTracks: List<Track> = emptyList()) {
        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first()
            android.util.Log.i("VIBE_PLAYBACK", ">>> Play Track Clicked: '${track.name}' by '${track.artists.firstOrNull()?.name}' (URI: ${track.uri}) | Mode: ${settings.playbackMode}")
            audioPlayer.playTrack(track, contextTracks)
        }
    }

    private fun playLocalCollection(tracks: List<Track>, startIndex: Int) {
        val targetTrack = tracks.getOrNull(startIndex) ?: tracks.firstOrNull() ?: return
        playLocalTrack(targetTrack, tracks)
    }

    private fun addTrackToQueue(track: Track) {
        lifecycleScope.launch {
            android.util.Log.i("VIBE_PLAYBACK", ">>> Add to Queue Clicked: '${track.name}' (URI: ${track.uri})")
            audioPlayer.addToQueue(track)
            android.widget.Toast.makeText(
                this@MainActivity,
                getString(com.vibe.core.ui.R.string.queue_added_toast) + ": " + track.name,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private suspend fun refreshDevicesAndSyncPlayback(autoSwitchIfRemoteActive: Boolean = true) {
        try {
            // 1. Fetch available devices from Web API
            val devicesResult = apiService.getAvailableDevices()
            val webApiDevices = devicesResult.getOrNull() ?: emptyList()
            if (webApiDevices.isNotEmpty()) {
                connectDeviceManager.syncWithWebApiDevices(webApiDevices)
            }

            // 2. Fetch current playback state
            val playbackResult = apiService.getPlaybackState()
            val state = playbackResult.getOrNull()

            val currentTarget = (audioPlayer as? RoutingAudioPlayerImpl)?.spotifyConnect?.targetDeviceId
            val matchingCurrent = webApiDevices.firstOrNull { it.id == currentTarget }

            // Determine active or target device (respecting current phone/device or active)
            val activeDevice = state?.activeDevice
                ?: webApiDevices.firstOrNull { it.isActive }
                ?: matchingCurrent
                ?: webApiDevices.firstOrNull { it.isCurrentDevice(webApiDevices) }
                ?: webApiDevices.firstOrNull()

            if (activeDevice != null) {
                (audioPlayer as? RoutingAudioPlayerImpl)?.spotifyConnect?.targetDeviceId = activeDevice.id
            }

            if (activeDevice != null && state?.isPlaying == true) {
                android.util.Log.i(
                    "VIBE_CONNECT",
                    "Active playback device detected: '${activeDevice.name}' (type=${activeDevice.type}, isPlaying=${state.isPlaying})"
                )
                if (autoSwitchIfRemoteActive) {
                    val currentSettings = settingsManager.settingsFlow.first()
                    if (currentSettings.playbackMode != PlaybackMode.CONNECT) {
                        android.util.Log.i(
                            "VIBE_CONNECT",
                            "Auto-switching playback mode to CONNECT for device '${activeDevice.name}'"
                        )
                        settingsManager.setPlaybackMode(PlaybackMode.CONNECT)
                    }
                    (audioPlayer as? RoutingAudioPlayerImpl)?.switchToConnect(activeDevice.id)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VIBE_CONNECT", "Error refreshing devices and playback sync", e)
        }
    }

    override fun onStart() {
        super.onStart()
        connectDeviceManager.startDiscovery()
        lifecycleScope.launch {
            refreshDevicesAndSyncPlayback(autoSwitchIfRemoteActive = true)
        }
        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first()
            if (settings.playbackMode == PlaybackMode.SPOTIFY_REMOTE && spotifyAppRemoteManager.isSpotifyInstalled()) {
                android.util.Log.i("VIBE_REMOTE", "Silently connecting to Spotify App Remote onStart...")
                spotifyAppRemoteManager.connect(showAuthView = false)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        connectDeviceManager.stopDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        spotifyAppRemoteManager.setActivity(null)
        spotifyAppRemoteManager.disconnect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme
        val host = data.host

        if (scheme == "vibe" && host == "auth") {
            lifecycleScope.launch {
                authManager.handleAuthCallback(data)
                    .onSuccess {
                        Toast.makeText(this@MainActivity, "Connected to Spotify!", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { err ->
                        Toast.makeText(this@MainActivity, "Login error: ${err.message}", Toast.LENGTH_LONG).show()
                    }
            }
        } else if (scheme == "spotify" || host == "open.spotify.com") {
            // Handle Spotify deep link
        }
    }
}

@Composable
fun LoginScreen(
    initialClientId: String = "",
    onLoginClick: (String) -> Unit
) {
    var clientId by remember(initialClientId) { mutableStateOf(initialClientId) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val redirectUri = SpotifyAuthConfig.REDIRECT_URI

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Spotify Developer Dashboard setup Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.client_id_dashboard_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = redirectUri,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(redirectUri))
                            Toast.makeText(
                                context,
                                context.getString(com.vibe.core.ui.R.string.client_id_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.client_id_redirect_uri_label),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.spotify.com/dashboard"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.client_id_open_dashboard))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = clientId,
            onValueChange = {
                clientId = it
                if (errorMessage != null) errorMessage = null
            },
            label = { Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.client_id_label)) },
            placeholder = { Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.client_id_hint)) },
            singleLine = true,
            isError = errorMessage != null,
            supportingText = if (errorMessage != null) {
                { Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val trimmed = clientId.trim()
                if (trimmed.isEmpty()) {
                    errorMessage = context.getString(com.vibe.core.ui.R.string.client_id_empty_error)
                } else {
                    onLoginClick(trimmed)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.connect_with_spotify),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
