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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.BackHandler
import com.vibe.core.connect.ConnectDeviceManager
import com.vibe.core.model.Album
import com.vibe.core.model.Artist
import com.vibe.core.model.Playlist
import com.vibe.core.network.DualSearchManager
import com.vibe.core.network.SpotifyApiService
import com.vibe.core.network.auth.AuthState
import com.vibe.core.network.auth.SpotifyAuthConfig
import com.vibe.core.network.auth.SpotifyAuthManager
import com.vibe.core.playback.VibeAudioPlayer
import com.vibe.core.ui.VibeTheme
import com.vibe.feature.album.AlbumScreen
import com.vibe.feature.artist.ArtistScreen
import com.vibe.feature.devices.DevicesDialog
import com.vibe.feature.home.HomeScreen
import com.vibe.feature.library.LibraryScreen
import com.vibe.feature.player.FullPlayerScreen
import com.vibe.feature.player.MiniPlayerBar
import com.vibe.feature.playlist.PlaylistScreen
import com.vibe.feature.search.SearchScreen
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val authManager: SpotifyAuthManager by inject()
    private val audioPlayer: VibeAudioPlayer by inject()
    private val apiService: SpotifyApiService by inject()
    private val connectDeviceManager: ConnectDeviceManager by inject()
    private val searchManager: DualSearchManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            val authState by authManager.authState.collectAsState(initial = AuthState.Loading)
            val playbackState by audioPlayer.playbackState.collectAsState()
            val devices by connectDeviceManager.devices.collectAsState()

            var currentScreen by remember { mutableStateOf("home") }
            var isFullPlayerVisible by remember { mutableStateOf(false) }
            var isDevicesDialogVisible by remember { mutableStateOf(false) }

            var activeArtist by remember { mutableStateOf<Artist?>(null) }
            var activeAlbum by remember { mutableStateOf<Album?>(null) }
            var activePlaylist by remember { mutableStateOf<Playlist?>(null) }
            var userPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
            var userLikedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

            LaunchedEffect(authState) {
                if (authState is AuthState.Authenticated) {
                    apiService.getCurrentUserPlaylists().onSuccess { pls ->
                        userPlaylists = pls
                    }
                    apiService.getLikedSongs(0, 20).onSuccess { tracks ->
                        userLikedTracks = tracks
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
                            Scaffold(
                                bottomBar = {
                                    Column {
                                        // Mini Player Bar docked above Navigation Bar
                                        if (playbackState.currentTrack != null) {
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

                                        NavigationBar {
                                            NavigationBarItem(
                                                selected = currentScreen == "home" && activeArtist == null && activeAlbum == null && activePlaylist == null,
                                                onClick = {
                                                    activeArtist = null
                                                    activeAlbum = null
                                                    activePlaylist = null
                                                    currentScreen = "home"
                                                },
                                                icon = { Icon(Icons.Default.Home, contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_home)) },
                                                label = { Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_home)) }
                                            )
                                            NavigationBarItem(
                                                selected = currentScreen == "search" && activeArtist == null && activeAlbum == null && activePlaylist == null,
                                                onClick = {
                                                    activeArtist = null
                                                    activeAlbum = null
                                                    activePlaylist = null
                                                    currentScreen = "search"
                                                },
                                                icon = { Icon(Icons.Default.Search, contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_search)) },
                                                label = { Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_search)) }
                                            )
                                            NavigationBarItem(
                                                selected = currentScreen == "library" && activeArtist == null && activeAlbum == null && activePlaylist == null,
                                                onClick = {
                                                    activeArtist = null
                                                    activeAlbum = null
                                                    activePlaylist = null
                                                    currentScreen = "library"
                                                },
                                                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_library)) },
                                                label = { Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_library)) }
                                            )
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                    if (activeArtist != null) {
                                        ArtistScreen(
                                            artist = activeArtist!!,
                                            onTrackClick = { track ->
                                                audioPlayer.playTrack(track, activeArtist!!.topTracks)
                                            },
                                            onAlbumClick = { albumId ->
                                                lifecycleScope.launch {
                                                    apiService.getAlbum(albumId).onSuccess { activeAlbum = it }
                                                }
                                            }
                                        )
                                        BackHandler { activeArtist = null }
                                    } else if (activeAlbum != null) {
                                        AlbumScreen(
                                            album = activeAlbum!!,
                                            onTrackClick = { track, index ->
                                                audioPlayer.playFilteredCollection(activeAlbum!!.tracks, index)
                                            }
                                        )
                                        BackHandler { activeAlbum = null }
                                    } else if (activePlaylist != null) {
                                        PlaylistScreen(
                                            playlist = activePlaylist!!,
                                            onPlayClick = {
                                                if (activePlaylist!!.tracks.isNotEmpty()) {
                                                    audioPlayer.playTrack(activePlaylist!!.tracks.first(), activePlaylist!!.tracks)
                                                }
                                            },
                                            onTrackClick = { track, index ->
                                                audioPlayer.playFilteredCollection(activePlaylist!!.tracks, index)
                                            }
                                        )
                                        BackHandler { activePlaylist = null }
                                    } else {
                                        when (currentScreen) {
                                            "home" -> HomeScreen(
                                                playlists = userPlaylists,
                                                recentTracks = userLikedTracks,
                                                onPlaylistClick = { playlistId ->
                                                    lifecycleScope.launch {
                                                        apiService.getPlaylist(playlistId).onSuccess { p ->
                                                            activePlaylist = p
                                                            if (p.tracks.isNotEmpty()) {
                                                                audioPlayer.playTrack(p.tracks.first(), p.tracks)
                                                            }
                                                        }
                                                    }
                                                },
                                                onTrackClick = { track, tracks ->
                                                    audioPlayer.playTrack(track, tracks)
                                                }
                                            )
                                            "search" -> SearchScreen(
                                                searchManager = searchManager,
                                                onTrackClick = { track, contextTracks ->
                                                    audioPlayer.playTrack(track, contextTracks)
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
                                                }
                                            )
                                            "library" -> LibraryScreen(
                                                playlists = userPlaylists,
                                                onOpenLikedSongs = {
                                                    lifecycleScope.launch {
                                                        apiService.getLikedSongs(0, 50).onSuccess { tracks ->
                                                            if (tracks.isNotEmpty()) {
                                                                audioPlayer.playFilteredCollection(tracks, 0)
                                                            }
                                                        }
                                                    }
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
                                                                audioPlayer.playTrack(p.tracks.first(), p.tracks)
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Full Player Screen Modal
                            if (isFullPlayerVisible && playbackState.currentTrack != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
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
                                        onOpenDevices = { isDevicesDialogVisible = true }
                                    )
                                }
                            }

                            // Connect Devices Dialog
                            if (isDevicesDialogVisible) {
                                DevicesDialog(
                                    devices = devices,
                                    onSelectDevice = { dev ->
                                        lifecycleScope.launch {
                                            apiService.transferPlayback(dev.id, play = true)
                                            isDevicesDialogVisible = false
                                        }
                                    },
                                    onVolumeChange = { dev, vol ->
                                        // Update device volume
                                    },
                                    onDismiss = { isDevicesDialogVisible = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectDeviceManager.startDiscovery()
        lifecycleScope.launch {
            apiService.getAvailableDevices().onSuccess { devs ->
                connectDeviceManager.syncWithWebApiDevices(devs)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        connectDeviceManager.stopDiscovery()
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
