package com.vibe.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vibe.core.playback.VibeAudioPlayer
import com.vibe.core.ui.VibeTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val audioPlayer: VibeAudioPlayer by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)

        setContent {
            val playbackState by audioPlayer.playbackState.collectAsState()
            var currentScreen by remember { mutableStateOf("home") }

            VibeTheme(
                isAlbumArtTintEnabled = true
            ) {
                Scaffold(
                    bottomBar = {
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
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (currentScreen) {
                            "home" -> Text("Home Screen")
                            "search" -> Text("Search Screen")
                            "library" -> Text("Library Screen")
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null) {
            val uriString = data.toString()
            if (uriString.startsWith("spotify:") || data.host == "open.spotify.com") {
                // Route to appropriate content (track, album, artist, playlist)
            }
        }
    }
}
