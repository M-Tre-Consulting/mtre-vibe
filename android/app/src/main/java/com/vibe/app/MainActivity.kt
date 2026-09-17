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
import com.vibe.core.connect.ConnectDeviceManager
import com.vibe.core.network.SpotifyApiService
import com.vibe.core.network.auth.AuthState
import com.vibe.core.network.auth.SpotifyAuthConfig
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
                                                onArtistClick = { /* Navigate to artist page */ }
                                            )
                                        }

                                        NavigationBar {
                                            NavigationBarItem(
                                                selected = currentScreen == "home",
                                                onClick = { currentScreen = "home" },
                                                icon = { Icon(Icons.Default.Home, contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_home)) },
                                                label = { Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_home)) }
                                            )
                                            NavigationBarItem(
                                                selected = currentScreen == "search",
                                                onClick = { currentScreen = "search" },
                                                icon = { Icon(Icons.Default.Search, contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_search)) },
                                                label = { Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_search)) }
                                            )
                                            NavigationBarItem(
                                                selected = currentScreen == "library",
                                                onClick = { currentScreen = "library" },
                                                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_library)) },
                                                label = { Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_library)) }
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
