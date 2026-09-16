package com.vibe.app

import android.app.Application
import com.vibe.core.playback.Media3AudioPlayerImpl
import com.vibe.core.playback.VibeAudioPlayer
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
    single<VibeAudioPlayer> { Media3AudioPlayerImpl(get()) }
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
