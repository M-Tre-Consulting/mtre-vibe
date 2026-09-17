package com.vibe.app.playback

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vibe.core.playback.Media3AudioPlayerImpl
import com.vibe.core.playback.VibeAudioPlayer
import org.koin.android.ext.android.inject

class VibeMediaSessionService : MediaSessionService() {

    private val audioPlayer: VibeAudioPlayer by inject()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return (audioPlayer as? Media3AudioPlayerImpl)?.mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = (audioPlayer as? Media3AudioPlayerImpl)?.mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
