package com.vibe.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vibe.core.playback.Media3AudioPlayerImpl
import com.vibe.core.playback.RoutingAudioPlayerImpl
import com.vibe.core.playback.VibeAudioPlayer
import org.koin.android.ext.android.inject

class VibeMediaSessionService : MediaSessionService() {

    private val audioPlayer: VibeAudioPlayer by inject()

    private val activeMediaSession: MediaSession?
        get() = (audioPlayer as? RoutingAudioPlayerImpl)?.mediaSession ?: (audioPlayer as? Media3AudioPlayerImpl)?.mediaSession

    companion object {
        private const val TAG = "VibeMediaSessionService"
        const val NOTIFICATION_CHANNEL_ID = "vibe_playback_channel"
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        attachSession()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (notificationManager?.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Vibe Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Vibe music playback notifications and controls"
                    setShowBadge(false)
                }
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }

    private fun attachSession() {
        try {
            val session = activeMediaSession
            if (session != null && !isSessionAdded(session)) {
                addSession(session)
                Log.d(TAG, "MediaSession successfully attached to VibeMediaSessionService")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching session: ${e.message}", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        attachSession()
        return activeMediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        attachSession()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = activeMediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            val session = activeMediaSession
            if (session != null && isSessionAdded(session)) {
                removeSession(session)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing session on destroy: ${e.message}", e)
        }
        super.onDestroy()
    }
}
