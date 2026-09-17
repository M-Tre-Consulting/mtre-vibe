package com.vibe.core.playback

import android.util.Log
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
 * High-performance audio stream resolver for 100% local on-device playback.
 * Resolves playable audio streams via:
 * 1. Pre-cached in-memory lookup
 * 2. Direct Spotify preview URL
 * 3. Spotify Embed audio extraction
 * 4. Deezer audio CDN fallback
 * 5. Apple iTunes audio CDN fallback
 */
object TrackAudioResolver {

    private const val TAG = "TrackAudioResolver"
    private val cache = ConcurrentHashMap<String, String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val SPOTIFY_PREVIEW_REGEX = Pattern.compile("https://p\\.scdn\\.co/mp3-preview/[a-f0-9]+")
    private val FEAT_REGEX = Pattern.compile("\\s*[({\\[](feat\\.|ft\\.|with).*?[)\\]}]", Pattern.CASE_INSENSITIVE)
    private val REMASTER_REGEX = Pattern.compile("\\s*-\\s*\\d{4}\\s*-?\\s*Remaster.*", Pattern.CASE_INSENSITIVE)

    suspend fun resolveAudioUrl(track: Track): String? = withContext(Dispatchers.IO) {
        val cleanTrackId = track.id.removePrefix("spotify:track:").substringAfterLast(":")

        // 1. Check in-memory cache
        cache[cleanTrackId]?.let {
            Log.d(TAG, "Cache hit for track: ${track.name} ($it)")
            return@withContext it
        }

        // 2. Direct HTTP preview if already valid
        val previewUrl = track.previewUrl
        if (!previewUrl.isNullOrBlank() &&
            (previewUrl.startsWith("http://") || previewUrl.startsWith("https://"))
        ) {
            cache[cleanTrackId] = previewUrl
            Log.d(TAG, "Direct previewUrl for ${track.name}: $previewUrl")
            return@withContext previewUrl
        }

        // 3. Try Spotify Embed extraction
        val spotifyEmbedUrl = resolveFromSpotifyEmbed(cleanTrackId)
        if (!spotifyEmbedUrl.isNullOrBlank()) {
            cache[cleanTrackId] = spotifyEmbedUrl
            Log.d(TAG, "Spotify Embed resolved ${track.name}: $spotifyEmbedUrl")
            return@withContext spotifyEmbedUrl
        }

        // 4. Try Deezer Search preview
        val deezerUrl = resolveFromDeezer(track)
        if (!deezerUrl.isNullOrBlank()) {
            cache[cleanTrackId] = deezerUrl
            Log.d(TAG, "Deezer CDN resolved ${track.name}: $deezerUrl")
            return@withContext deezerUrl
        }

        // 5. Try iTunes Search preview
        val itunesUrl = resolveFromITunes(track)
        if (!itunesUrl.isNullOrBlank()) {
            cache[cleanTrackId] = itunesUrl
            Log.d(TAG, "iTunes resolved ${track.name}: $itunesUrl")
            return@withContext itunesUrl
        }

        Log.w(TAG, "Failed to resolve audio stream for track: ${track.name} (${track.id})")
        null
    }

    private fun resolveFromSpotifyEmbed(cleanTrackId: String): String? {
        return try {
            val request = Request.Builder()
                .url("https://open.spotify.com/embed/track/$cleanTrackId")
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
        } catch (e: Exception) {
            Log.d(TAG, "Spotify Embed extraction failed for $cleanTrackId: ${e.message}")
            null
        }
    }

    private fun resolveFromDeezer(track: Track): String? {
        val artist = track.artists.firstOrNull()?.name ?: ""
        // Try raw title first, then cleaned title
        val titlesToTry = listOf(track.name, cleanTitle(track.name)).distinct()

        for (title in titlesToTry) {
            try {
                val query = URLEncoder.encode("$artist $title", "UTF-8")
                val request = Request.Builder()
                    .url("https://api.deezer.com/search?q=$query&limit=1")
                    .header("User-Agent", "Vibe/1.0 (Android)")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: continue
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
            } catch (e: Exception) {
                Log.d(TAG, "Deezer query failed for '$artist $title': ${e.message}")
            }
        }
        return null
    }

    private fun resolveFromITunes(track: Track): String? {
        val artist = track.artists.firstOrNull()?.name ?: ""
        val titlesToTry = listOf(track.name, cleanTitle(track.name)).distinct()

        for (title in titlesToTry) {
            try {
                val query = URLEncoder.encode("$artist $title", "UTF-8")
                val request = Request.Builder()
                    .url("https://itunes.apple.com/search?term=$query&media=music&limit=1")
                    .header("User-Agent", "Vibe/1.0 (Android)")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: continue
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
            } catch (e: Exception) {
                Log.d(TAG, "iTunes query failed for '$artist $title': ${e.message}")
            }
        }
        return null
    }

    private fun cleanTitle(title: String): String {
        var clean = FEAT_REGEX.matcher(title).replaceAll("")
        clean = REMASTER_REGEX.matcher(clean).replaceAll("")
        return clean.trim()
    }
}
