package com.vibe.core.playback

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibe.core.model.AudioQuality
import com.vibe.core.model.PlaybackMode
import com.vibe.core.model.VibeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "vibe_settings_prefs")

class SettingsManager(private val context: Context) {
    companion object {
        private val KEY_PLAYBACK_MODE = stringPreferencesKey("playback_mode")
        private val KEY_AUTO_FALLBACK = booleanPreferencesKey("auto_fallback")
        private val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        private val KEY_LIBRESPOT_ENABLED = booleanPreferencesKey("librespot_enabled")
    }

    val settingsFlow: Flow<VibeSettings> = context.settingsDataStore.data.map { prefs ->
        val modeStr = prefs[KEY_PLAYBACK_MODE] ?: PlaybackMode.STANDALONE.name
        val mode = runCatching { PlaybackMode.valueOf(modeStr) }.getOrDefault(PlaybackMode.STANDALONE)
        val qualityStr = prefs[KEY_AUDIO_QUALITY] ?: AudioQuality.HIGH.name
        val quality = runCatching { AudioQuality.valueOf(qualityStr) }.getOrDefault(AudioQuality.HIGH)
        val autoFallback = prefs[KEY_AUTO_FALLBACK] ?: true
        val librespotEnabled = prefs[KEY_LIBRESPOT_ENABLED] ?: false

        VibeSettings(
            playbackMode = mode,
            autoFallbackEnabled = autoFallback,
            audioQuality = quality,
            isLibrespotEnabled = librespotEnabled
        )
    }

    suspend fun setPlaybackMode(mode: PlaybackMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_PLAYBACK_MODE] = mode.name
        }
    }

    suspend fun setAutoFallback(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AUTO_FALLBACK] = enabled
        }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AUDIO_QUALITY] = quality.name
        }
    }

    suspend fun setLibrespotEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LIBRESPOT_ENABLED] = enabled
        }
    }
}
