package com.vibe.core.playback

import com.vibe.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * High-performance audio stream resolver.
 * Resolves playable audio streams via:
 * 1. Pre-cached in-memory lookup
 * 2. Direct Spotify preview URL
 * 3. Spotify Embed audio extraction
 * 4. Deezer audio CDN fallback
 * 5. Apple iTunes audio CDN fallback
 */
object TrackAudioResolver {

    private val cache = ConcurrentHashMap<String, String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val SPOTIFY_PREVIEW_REGEX = Pattern.compile("https://p\\.scdn\\.co/mp3-preview/[a-f0-9]+")

    suspend fun resolveAudioUrl(track: Track): String? = withContext(Dispatchers.IO) {
        // 1. Check in-memory cache
        cache[track.id]?.let { return@withContext it }

        // 2. Direct HTTP preview if already valid
        val previewUrl = track.previewUrl
        if (!previewUrl.isNullOrBlank() &&
            (previewUrl.startsWith("http://") || previewUrl.startsWith("https://"))
        ) {
            cache[track.id] = previewUrl
            return@withContext previewUrl
        }

        // 3. Try Spotify Embed extraction
        val spotifyEmbedUrl = resolveFromSpotifyEmbed(track.id)
        if (!spotifyEmbedUrl.isNullOrBlank()) {
            cache[track.id] = spotifyEmbedUrl
            return@withContext spotifyEmbedUrl
        }

        // 4. Try Deezer Search preview
        val deezerUrl = resolveFromDeezer(track)
        if (!deezerUrl.isNullOrBlank()) {
            cache[track.id] = deezerUrl
            return@withContext deezerUrl
        }

        // 5. Try iTunes Search preview
        val itunesUrl = resolveFromITunes(track)
        if (!itunesUrl.isNullOrBlank()) {
            cache[track.id] = itunesUrl
            return@withContext itunesUrl
        }

        null
    }

    private fun resolveFromSpotifyEmbed(trackId: String): String? {
        return try {
            val request = Request.Builder()
                .url("https://open.spotify.com/embed/track/$trackId")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val matcher = SPOTIFY_PREVIEW_REGEX.matcher(html)
                if (matcher.find()) {
                    return matcher.group(0)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveFromDeezer(track: Track): String? {
        return try {
            val artist = track.artists.firstOrNull()?.name ?: ""
            val query = URLEncoder.encode("$artist ${track.name}", "UTF-8")
            val request = Request.Builder()
                .url("https://api.deezer.com/search?q=$query&limit=1")
                .header("User-Agent", "Vibe/1.0")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return null
                val root = JSONObject(jsonStr)
                val data = root.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val first = data.getJSONObject(0)
                    val preview = first.optString("preview")
                    if (!preview.isNullOrBlank() && preview.startsWith("http")) {
                        return preview
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveFromITunes(track: Track): String? {
        return try {
            val artist = track.artists.firstOrNull()?.name ?: ""
            val query = URLEncoder.encode("$artist ${track.name}", "UTF-8")
            val request = Request.Builder()
                .url("https://itunes.apple.com/search?term=$query&media=music&limit=1")
                .header("User-Agent", "Vibe/1.0")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return null
                val root = JSONObject(jsonStr)
                val results = root.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val preview = first.optString("previewUrl")
                    if (!preview.isNullOrBlank() && preview.startsWith("http")) {
                        return preview
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
