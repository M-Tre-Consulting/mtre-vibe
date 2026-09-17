package com.vibe.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vibe.core.connect.ConnectDeviceManager
import com.vibe.core.network.SpotifyApiService
import com.vibe.core.network.auth.AuthState
import com.vibe.core.network.auth.SpotifyAuthManager
import com.vibe.core.playback.VibeAudioPlayer
import com.vibe.core.ui.VibeTheme
import com.vibe.feature.devices.DevicesDialog
import com.vibe.feature.home.HomeScreen
import com.vibe.feature.library.LibraryScreen
import com.vibe.feature.player.FullPlayerScreen
import com.vibe.feature.player.MiniPlayerBar
import com.vibe.feature.search.SearchScreen
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val authManager: SpotifyAuthManager by inject()
    private val audioPlayer: VibeAudioPlayer by inject()
    private val apiService: SpotifyApiService by inject()
    private val connectDeviceManager: ConnectDeviceManager by inject()

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
                            LoginScreen(
                                onLoginClick = {
                                    lifecycleScope.launch {
                                        authManager.launchLogin(this@MainActivity)
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
                                                onArtistClick = { /* Navigate to artist page */ }
                                            )
                                        }

                                        NavigationBar {
                                            NavigationBarItem(
                                                selected = currentScreen == "home",
                                                onClick = { currentScreen = "home" },
                                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                                label = { Text("Home") }
                                            )
                                            NavigationBarItem(
                                                selected = currentScreen == "search",
                                                onClick = { currentScreen = "search" },
                                                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                                label = { Text("Search") }
                                            )
                                            NavigationBarItem(
                                                selected = currentScreen == "library",
                                                onClick = { currentScreen = "library" },
                                                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                                                label = { Text("Library") }
                                            )
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                    when (currentScreen) {
                                        "home" -> HomeScreen(
                                            onPlaylistClick = { playlistId ->
                                                lifecycleScope.launch {
                                                    apiService.getPlaylist(playlistId).onSuccess { p ->
                                                        if (p.tracks.isNotEmpty()) {
                                                            audioPlayer.playTrack(p.tracks.first(), p.tracks)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                        "search" -> SearchScreen(
                                            onQueryChange = { query ->
                                                // Trigger search via apiService
                                            }
                                        )
                                        "library" -> LibraryScreen(
                                            onOpenLikedSongs = {
                                                lifecycleScope.launch {
                                                    apiService.getLikedSongs(0, 50).onSuccess { tracks ->
                                                        if (tracks.isNotEmpty()) {
                                                            audioPlayer.playFilteredCollection(tracks, 0)
                                                        }
                                                    }
                                                }
                                            }
                                        )
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
        val data: Uri? = intent?.data ?: return
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
fun LoginScreen(onLoginClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Welcome to Vibe",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "High-performance native client for Spotify. Connect with your account to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Connect with Spotify", style = MaterialTheme.typography.titleMedium)
        }
    }
}
