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

val appModule = module {
    single { SpotifyAuthManager(context = get()) }

    single<SpotifyApiService> {
        val authManager: SpotifyAuthManager = get()
        SpotifyClientFactory.createSpotifyApiService(authManager, enableLogging = true)
    }

    single<VibeAudioPlayer> {
        val authManager: SpotifyAuthManager = get()
        Media3AudioPlayerImpl(
            context = get(),
            tokenProvider = { runBlocking { authManager.getValidAccessToken() } }
        )
    }

    single { ConnectDeviceManager(context = get()) }

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
