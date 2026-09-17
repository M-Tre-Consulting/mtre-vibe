package com.vibe.core.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Custom Media3 DataSource for Spotify streams.
 * Implements resilient 5-second timeout failover across alternative CDN endpoints.
 */
@OptIn(UnstableApi::class)
class SpotifyAudioDataSource(
    private val tokenProvider: () -> String?,
    private val cdnEndpoints: List<String> = listOf(
        "https://audio-fa.scdn.co",
        "https://audio-ak.scdn.co",
        "https://audio4-fa.scdn.co"
    )
) : BaseDataSource(/* isNetwork = */ true) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var dataSpec: DataSpec? = null
    private var inputStream: InputStream? = null
    private var response: Response? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri

        // If direct HTTP/HTTPS stream (e.g. preview MP3/AAC URL), stream directly
        if (uri.scheme == "http" || uri.scheme == "https") {
            val requestBuilder = Request.Builder().url(uri.toString())
            tokenProvider()?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }
            if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
                val rangeEnd = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    dataSpec.position + dataSpec.length - 1
                } else ""
                requestBuilder.header("Range", "bytes=${dataSpec.position}-$rangeEnd")
            }
            val resp = httpClient.newCall(requestBuilder.build()).execute()
            if (resp.isSuccessful) {
                this.response = resp
                val body = resp.body ?: throw IOException("Empty response body from $uri")
                this.inputStream = body.byteStream()
                val contentLength = body.contentLength()

                bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    dataSpec.length
                } else if (contentLength != -1L) {
                    contentLength
                } else {
                    C.LENGTH_UNSET.toLong()
                }

                opened = true
                transferStarted(dataSpec)
                return bytesRemaining
            } else {
                resp.close()
                throw IOException("HTTP error ${resp.code} fetching $uri")
            }
        }

        val trackId = if (uri.scheme == "spotify") {
            uri.schemeSpecificPart.removePrefix("track:")
        } else {
            uri.lastPathSegment ?: uri.toString()
        }

        // Try primary and fallback CDN endpoints with 5s timeout per attempt
        var lastException: IOException? = null
        for (cdnBase in cdnEndpoints) {
            try {
                val targetUrl = "$cdnBase/audio/$trackId"
                val requestBuilder = Request.Builder().url(targetUrl)

                tokenProvider()?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    val rangeEnd = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                        dataSpec.position + dataSpec.length - 1
                    } else ""
                    requestBuilder.header("Range", "bytes=${dataSpec.position}-$rangeEnd")
                }

                val resp = httpClient.newCall(requestBuilder.build()).execute()
                if (resp.isSuccessful) {
                    this.response = resp
                    val body = resp.body ?: throw IOException("Empty response body from $cdnBase")
                    this.inputStream = body.byteStream()
                    val contentLength = body.contentLength()

                    bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                        dataSpec.length
                    } else if (contentLength != -1L) {
                        contentLength
                    } else {
                        C.LENGTH_UNSET.toLong()
                    }

                    opened = true
                    transferStarted(dataSpec)
                    return bytesRemaining
                } else {
                    resp.close()
                }
            } catch (e: IOException) {
                // Stalled connection (5s timeout) or network error: attempt next CDN endpoint
                lastException = e
            }
        }

        throw lastException ?: IOException("Failed to open audio stream from all CDN endpoints")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }

        val bytesRead = inputStream?.read(buffer, offset, bytesToRead) ?: C.RESULT_END_OF_INPUT
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        try {
            inputStream?.close()
        } finally {
            inputStream = null
            response?.close()
            response = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
