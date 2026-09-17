package com.vibe.app

import android.app.Application
import com.vibe.core.connect.ConnectDeviceManager
import com.vibe.core.database.LikedSongsCacheManager
import com.vibe.core.network.SpotifyApiService
import com.vibe.core.network.SpotifyClientFactory
import com.vibe.core.network.auth.SpotifyAuthManager
import com.vibe.core.playback.Media3AudioPlayerImpl
import com.vibe.core.playback.VibeAudioPlayer
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

import com.vibe.core.network.DualSearchManager
import com.vibe.core.network.auth.SpotifyAuthConfig

val appModule = module {
    single {
        val configuredId = BuildConfig.SPOTIFY_CLIENT_ID.trim()
        val defaultId = if (configuredId.isNotEmpty()) configuredId else SpotifyAuthConfig.DEFAULT_CLIENT_ID
        SpotifyAuthManager(
            context = get(),
            clientId = defaultId
        )
    }

    single<SpotifyApiService> {
        val authManager: SpotifyAuthManager = get()
        SpotifyClientFactory.createSpotifyApiService(authManager, enableLogging = true)
    }

    single<VibeAudioPlayer> {
        val authManager: SpotifyAuthManager = get()
        val apiService: SpotifyApiService = get()
        Media3AudioPlayerImpl(
            context = get(),
            tokenProvider = { runBlocking { authManager.getValidAccessToken() } },
            remotePlaybackProvider = { uris ->
                apiService.startPlayback(uris = uris)
            }
        )
    }

    single { ConnectDeviceManager(context = get()) }

    single { DualSearchManager(apiService = get()) }

    single {
        LikedSongsCacheManager(
            accountId = "current_user",
            baseCacheDir = get<Application>().cacheDir
        )
    }
}

class VibeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@VibeApplication)
            modules(appModule)
        }
    }
}
