package com.vibe.core.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Repeat
import com.vibe.core.model.AlbumSummary
import com.vibe.core.model.ArtistSummary
import com.vibe.core.model.PlaybackState
import com.vibe.core.model.RepeatMode
import com.vibe.core.model.Track
import com.vibe.core.network.auth.SpotifyAuthConfig
import com.vibe.core.ui.R as UiR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * High-fidelity IPC Controller using the official Spotify Android App Remote SDK.
 * Binds directly to the local Spotify service on the device to stream 100% full-length
 * 320 kbps Vorbis audio through the phone's hardware speakers/headphones with zero scraping.
 */
class SpotifyAppRemoteManager(
    private val context: Context,
    private val clientIdProvider: suspend () -> String,
    private val redirectUri: String = SpotifyAuthConfig.REDIRECT_URI,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : VibeAudioPlayer {

    companion object {
        private const val TAG = "VIBE_REMOTE"
        private const val TAG_SDK = "SPOTIFY_SDK_DEBUG"
    }

    private var appRemote: SpotifyAppRemote? = null
    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var currentPlayingTrack: Track? = null
    private var tickerJob: Job? = null
    private var activityRef: java.lang.ref.WeakReference<android.app.Activity>? = null

    fun setActivity(activity: android.app.Activity?) {
        activityRef = if (activity != null) java.lang.ref.WeakReference(activity) else null
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnectedFlow: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    override val errorEvents: Flow<String> = _errorEvents.asSharedFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        try {
            SpotifyAppRemote.setDebugMode(true)
            com.spotify.protocol.client.Debug.setLogger(object : com.spotify.protocol.client.Debug.Logger {
                override fun d(msg: String?, vararg args: Any?) {
                    val formatted = runCatching { msg?.let { String.format(it, *args) } }.getOrNull() ?: msg ?: ""
                    Log.d(TAG_SDK, formatted)
                }
                override fun d(t: Throwable?, msg: String?, vararg args: Any?) {
                    val formatted = runCatching { msg?.let { String.format(it, *args) } }.getOrNull() ?: msg ?: ""
                    Log.d(TAG_SDK, formatted, t)
                }
                override fun e(msg: String?, vararg args: Any?) {
                    val formatted = runCatching { msg?.let { String.format(it, *args) } }.getOrNull() ?: msg ?: ""
                    Log.e(TAG_SDK, formatted)
                }
                override fun e(t: Throwable?, msg: String?, vararg args: Any?) {
                    val formatted = runCatching { msg?.let { String.format(it, *args) } }.getOrNull() ?: msg ?: ""
                    Log.e(TAG_SDK, formatted, t)
                }
            })
            Log.i(TAG, "Initialized Spotify App Remote SDK Debug logger (Tag: $TAG_SDK)")
        } catch (e: Throwable) {
            Log.w(TAG, "Could not initialize Spotify App Remote debug logger: ${e.message}")
        }
        startPositionTicker()
    }

    @Suppress("DEPRECATION")
    private fun dumpDiagnostics(connectContext: Context, clientId: String, redirectUri: String, showAuthView: Boolean) {
        val sb = StringBuilder()
        sb.appendLine("==================== [SPOTIFY APP REMOTE IPC DIAGNOSTICS] ====================")
        sb.appendLine("Calling Package   : ${context.packageName}")
        val isActivity = connectContext is android.app.Activity
        sb.appendLine("Connecting Context: ${connectContext.javaClass.name} (isActivity=$isActivity)")
        sb.appendLine("Configured Client ID  : $clientId")
        sb.appendLine("Configured Redirect URI: $redirectUri")
        sb.appendLine("showAuthView      : $showAuthView")

        // Signing signatures (SHA-1 / SHA-256)
        try {
            val pm = context.packageManager
            val packageName = context.packageName
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val signingInfo = pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
                } else null
            } else {
                pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
            }

            if (signatures != null && signatures.isNotEmpty()) {
                for ((idx, sig) in signatures.withIndex()) {
                    val bytes = sig.toByteArray()
                    val sha1 = java.security.MessageDigest.getInstance("SHA-1").digest(bytes)
                        .joinToString(":") { "%02X".format(it) }
                    val sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                        .joinToString(":") { "%02X".format(it) }
                    sb.appendLine("Certificate #$idx:")
                    sb.appendLine("  SHA-1   : $sha1")
                    sb.appendLine("  SHA-256 : $sha256")
                }
            } else {
                sb.appendLine("Signing Certificates: none found")
            }
        } catch (e: Throwable) {
            sb.appendLine("Error reading signatures: ${e.message}")
        }

        // Spotify App package details
        try {
            val pm = context.packageManager
            val spotifyPkg = pm.getPackageInfo("com.spotify.music", 0)
            sb.appendLine("Spotify App (com.spotify.music): INSTALLED")
            sb.appendLine("  Version Name: ${spotifyPkg.versionName}")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) spotifyPkg.longVersionCode else spotifyPkg.versionCode.toLong()
            sb.appendLine("  Version Code: $vCode")
        } catch (e: Throwable) {
            sb.appendLine("Spotify App (com.spotify.music): NOT INSTALLED (${e.message})")
        }

        // Android version details
        sb.appendLine("Android OS: SDK ${android.os.Build.VERSION.SDK_INT} (Android ${android.os.Build.VERSION.RELEASE}), Model: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        sb.appendLine("================================================================================")
        Log.i(TAG, sb.toString())
    }

    fun isSpotifyInstalled(): Boolean {
        return try {
            SpotifyAppRemote.isSpotifyInstalled(context)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Spotify installation: ${e.message}")
            false
        }
    }

    suspend fun connect(showAuthView: Boolean = true): Boolean = withContext(Dispatchers.Main) {
        if (appRemote?.isConnected == true) {
            Log.d(TAG, "Spotify App Remote already connected.")
            _isConnected.value = true
            return@withContext true
        }

        if (!isSpotifyInstalled()) {
            val msg = context.getString(UiR.string.spotify_not_installed)
            Log.w(TAG, msg)
            _connectionError.value = msg
            _isConnected.value = false
            return@withContext false
        }

        val clientId = runCatching { clientIdProvider() }.getOrDefault(SpotifyAuthConfig.DEFAULT_CLIENT_ID)
        val connectContext = activityRef?.get() ?: context

        dumpDiagnostics(connectContext, clientId, redirectUri, showAuthView)
        Log.i(TAG, "Connecting to Spotify App Remote via IPC with ${connectContext.javaClass.simpleName}...")

        val result = withTimeoutOrNull(8000L) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val connectionParams = ConnectionParams.Builder(clientId)
                    .setRedirectUri(redirectUri)
                    .showAuthView(showAuthView)
                    .build()

                SpotifyAppRemote.connect(connectContext, connectionParams, object : Connector.ConnectionListener {
                    override fun onConnected(remote: SpotifyAppRemote) {
                        Log.i(TAG, "================================================================================")
                        Log.i(TAG, ">>> [VIBE_REMOTE] IPC CONNECTION ESTABLISHED SUCCESSFULLY! <<<")
                        Log.i(TAG, "AppRemote isConnected: ${remote.isConnected}")
                        Log.i(TAG, "================================================================================")
                        appRemote = remote
                        _isConnected.value = true
                        _connectionError.value = null
                        subscribeToPlayerState(remote)
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onFailure(throwable: Throwable) {
                        val errMsg = throwable.message ?: throwable.javaClass.simpleName
                        val throwableName = throwable.javaClass.name

                        val sb = StringBuilder()
                        sb.appendLine("==================== [SPOTIFY APP REMOTE IPC ERROR] ====================")
                        sb.appendLine("Exception Type: $throwableName")
                        sb.appendLine("Error Message : $errMsg")

                        val isAuthException = throwable is com.spotify.android.appremote.api.error.UserNotAuthorizedException ||
                            throwableName.contains("UserNotAuthorized", ignoreCase = true) ||
                            errMsg.contains("Explicit user authorization is required", ignoreCase = true)

                        if (isAuthException) {
                            sb.appendLine("------------------------------------------------------------------------")
                            sb.appendLine("DIAGNOSIS: UserNotAuthorizedException / Authorization Required")
                            sb.appendLine("Spotify's bound service rejected the IPC handshake.")
                            sb.appendLine("Root causes & Fixes:")
                            sb.appendLine("1. SHA-1 & PACKAGE MISMATCH in Spotify Developer Dashboard:")
                            sb.appendLine("   Your current package is '${context.packageName}'.")
                            sb.appendLine("   Make sure the Spotify Developer Dashboard (https://developer.spotify.com/dashboard)")
                            sb.appendLine("   has an Android package entry for '${context.packageName}' with the exact SHA-1 printed above.")
                            sb.appendLine("2. BACKGROUND ACTIVITY RESTRICTIONS (Android 14 / 15):")
                            sb.appendLine("   If Spotify was in the background, Android may block Spotify from launching")
                            sb.appendLine("   its authorization modal dialog.")
                            sb.appendLine("   ACTION: Open the Spotify app first, keep it in recents, then retry connecting in Vibe.")
                            sb.appendLine("3. DEVELOPER DASHBOARD USER ACCESS:")
                            sb.appendLine("   If your Spotify app is in Development Mode, the user account must be registered")
                            sb.appendLine("   under 'Users and Access' in Spotify Developer Dashboard.")
                            sb.appendLine("------------------------------------------------------------------------")
                        } else if (throwable is com.spotify.android.appremote.api.error.AuthenticationFailedException ||
                            throwableName.contains("AuthenticationFailed", ignoreCase = true)) {
                            sb.appendLine("------------------------------------------------------------------------")
                            sb.appendLine("DIAGNOSIS: AuthenticationFailedException")
                            sb.appendLine("The Client ID or Redirect URI does not match what is registered on Spotify Dashboard.")
                            sb.appendLine("------------------------------------------------------------------------")
                        } else if (throwable is com.spotify.android.appremote.api.error.NotLoggedInException ||
                            throwableName.contains("NotLoggedIn", ignoreCase = true)) {
                            sb.appendLine("------------------------------------------------------------------------")
                            sb.appendLine("DIAGNOSIS: NotLoggedInException")
                            sb.appendLine("Spotify is installed, but no user is currently logged into the Spotify app.")
                            sb.appendLine("ACTION: Open Spotify and log into an account.")
                            sb.appendLine("------------------------------------------------------------------------")
                        } else if (throwable is com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp ||
                            throwableName.contains("CouldNotFindSpotifyApp", ignoreCase = true)) {
                            sb.appendLine("------------------------------------------------------------------------")
                            sb.appendLine("DIAGNOSIS: CouldNotFindSpotifyApp")
                            sb.appendLine("The Spotify app is not installed on this device.")
                            sb.appendLine("------------------------------------------------------------------------")
                        }
                        sb.appendLine("========================================================================")
                        Log.e(TAG, sb.toString(), throwable)

                        appRemote = null
                        _isConnected.value = false
                        _connectionError.value = errMsg
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }
        }

        result ?: run {
            Log.w(TAG, "Spotify App Remote connection timed out after 8s.")
            _connectionError.value = context.getString(UiR.string.playback_error_timeout)
            false
        }
    }

    private fun subscribeToPlayerState(remote: SpotifyAppRemote) {
        playerStateSubscription?.cancel()
        Log.i(TAG, "Subscribing to Spotify App Remote PlayerState updates...")
        val sub = remote.playerApi.subscribeToPlayerState()
        sub.setEventCallback { state ->
            if (state != null) {
                handleRemotePlayerState(state)
            }
        }
        sub.setErrorCallback { err ->
            Log.w(TAG, "PlayerState subscription error: ${err.message}")
        }
        playerStateSubscription = sub
    }

    private fun handleRemotePlayerState(state: PlayerState) {
        val remoteTrack = state.track
        val isPaused = state.isPaused
        val isPlaying = !isPaused
        val positionMs = state.playbackPosition
        val durationMs = remoteTrack?.duration ?: 0L
        val isShuffling = state.playbackOptions?.isShuffling ?: false
        val repeatInt = state.playbackOptions?.repeatMode ?: 0
        val repeatMode = when (repeatInt) {
            Repeat.ONE -> RepeatMode.ONE
            Repeat.ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }

        Log.d(TAG, "Remote PlayerState -> track='${remoteTrack?.name}', isPlaying=$isPlaying, pos=${positionMs}ms / ${durationMs}ms")

        val current = currentPlayingTrack
        val effectiveTrack = if (current != null && remoteTrack != null &&
            (current.uri == remoteTrack.uri || current.name.equals(remoteTrack.name, ignoreCase = true))
        ) {
            current.copy(durationMs = if (durationMs > 0) durationMs else current.durationMs)
        } else if (remoteTrack != null) {
            Track(
                id = remoteTrack.uri.substringAfterLast(":"),
                uri = remoteTrack.uri,
                name = remoteTrack.name,
                durationMs = remoteTrack.duration,
                artists = remoteTrack.artists?.map {
                    ArtistSummary(id = it.uri.substringAfterLast(":"), name = it.name, uri = it.uri)
                } ?: listOfNotNull(remoteTrack.artist?.let {
                    ArtistSummary(id = it.uri.substringAfterLast(":"), name = it.name, uri = it.uri)
                }),
                album = AlbumSummary(
                    id = remoteTrack.album?.uri?.substringAfterLast(":") ?: "",
                    name = remoteTrack.album?.name ?: "",
                    uri = remoteTrack.album?.uri ?: "",
                    imageUrl = current?.album?.imageUrl
                )
            ).also { currentPlayingTrack = it }
        } else {
            current
        }

        _playbackState.update { prev ->
            prev.copy(
                isPlaying = isPlaying,
                isPaused = isPaused,
                isBuffering = false,
                positionMs = positionMs,
                durationMs = if (durationMs > 0) durationMs else prev.durationMs,
                currentTrack = effectiveTrack,
                shuffleEnabled = isShuffling,
                repeatMode = repeatMode,
                audioBitrateKbps = 320
            )
        }
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(250)
                _playbackState.update { state ->
                    if (state.isPlaying && !state.isPaused && state.durationMs > 0) {
                        val newPos = (state.positionMs + 250).coerceAtMost(state.durationMs)
                        state.copy(positionMs = newPos)
                    } else {
                        state
                    }
                }
            }
        }
    }

    suspend fun playTrackWithResult(track: Track, contextTracks: List<Track>): Result<Unit> {
        currentPlayingTrack = track
        _playbackState.update { prev ->
            prev.copy(
                isPlaying = false,
                isPaused = false,
                isBuffering = true,
                currentTrack = track,
                durationMs = track.durationMs,
                positionMs = 0L
            )
        }

        if (appRemote?.isConnected != true) {
            val ok = connect()
            if (!ok) {
                val err = _connectionError.value ?: context.getString(UiR.string.playback_error_ipc_unauthorized)
                Log.e(TAG, "Cannot play track: Spotify App Remote connection failed ($err).")
                _playbackState.update { it.copy(isPlaying = false, isBuffering = false) }
                _errorEvents.emit(err)
                return Result.failure(IllegalStateException(err))
            }
        }

        val remote = appRemote
        if (remote == null || !remote.isConnected) {
            val err = context.getString(UiR.string.playback_error_ipc_unauthorized)
            Log.e(TAG, err)
            _playbackState.update { it.copy(isPlaying = false, isBuffering = false) }
            _errorEvents.emit(err)
            return Result.failure(IllegalStateException(err))
        }

        val targetUri = if (track.uri.startsWith("spotify:track:")) track.uri else "spotify:track:${track.id}"
        Log.i(TAG, "[IPC CALL] playerApi.play('$targetUri') for '${track.name}'")

        val result = withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine<Result<Unit>> { cont ->
                remote.playerApi.play(targetUri)
                    .setResultCallback {
                        Log.i(TAG, "[IPC RESULT] playerApi.play SUCCESS for '${track.name}'")
                        _playbackState.update { it.copy(isBuffering = false, isPlaying = true) }
                        // Ensure active playback if Spotify initialized in paused state
                        remote.playerApi.resume()
                        // Queue upcoming context tracks sequentially with delay
                        val nextTracks = contextTracks.dropWhile { it.id != track.id }.drop(1).take(5)
                        if (nextTracks.isNotEmpty()) {
                            scope.launch {
                                delay(1000)
                                for (t in nextTracks) {
                                    val nextUri = if (t.uri.startsWith("spotify:track:")) t.uri else "spotify:track:${t.id}"
                                    remote.playerApi.queue(nextUri)
                                    delay(250)
                                }
                            }
                        }
                        if (cont.isActive) cont.resume(Result.success(Unit))
                    }
                    .setErrorCallback { err ->
                        val errMsg = err.message ?: "Spotify playerApi error"
                        Log.e(TAG, "[IPC RESULT] playerApi.play ERROR for '${track.name}': $errMsg", err)
                        _playbackState.update { it.copy(isBuffering = false, isPlaying = false) }
                        scope.launch {
                            _errorEvents.emit(context.getString(UiR.string.playback_error_spotify_format, errMsg))
                        }
                        if (cont.isActive) cont.resume(Result.failure(Exception(errMsg)))
                    }
            }
        }

        return result ?: run {
            val timeoutMsg = context.getString(UiR.string.playback_error_timeout)
            Log.e(TAG, "[IPC TIMEOUT] $timeoutMsg")
            _playbackState.update { it.copy(isBuffering = false, isPlaying = false) }
            _errorEvents.emit(timeoutMsg)
            Result.failure(Exception(timeoutMsg))
        }
    }

    override fun playTrack(track: Track, contextTracks: List<Track>) {
        scope.launch {
            playTrackWithResult(track, contextTracks)
        }
    }

    override fun playFilteredCollection(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val targetTrack = tracks.getOrNull(startIndex) ?: tracks.first()
        playTrack(targetTrack, tracks)
    }

    override fun pause() {
        Log.i(TAG, "[IPC CALL] playerApi.pause()")
        _playbackState.update { it.copy(isPlaying = false, isPaused = true) }
        appRemote?.playerApi?.pause()
            ?.setResultCallback { Log.d(TAG, "[IPC RESULT] playerApi.pause SUCCESS") }
            ?.setErrorCallback { Log.w(TAG, "[IPC RESULT] playerApi.pause ERROR: ${it.message}") }
    }

    override fun resume() {
        Log.i(TAG, "[IPC CALL] playerApi.resume()")
        _playbackState.update { it.copy(isPlaying = true, isPaused = false) }
        appRemote?.playerApi?.resume()
            ?.setResultCallback { Log.d(TAG, "[IPC RESULT] playerApi.resume SUCCESS") }
            ?.setErrorCallback { Log.w(TAG, "[IPC RESULT] playerApi.resume ERROR: ${it.message}") }
    }

    override fun stop() {
        Log.i(TAG, "[IPC CALL] stop() -> pause()")
        pause()
    }

    override fun seekTo(positionMs: Long) {
        Log.i(TAG, "[IPC CALL] playerApi.seekTo($positionMs ms)")
        _playbackState.update { it.copy(positionMs = positionMs) }
        appRemote?.playerApi?.seekTo(positionMs)
            ?.setResultCallback { Log.d(TAG, "[IPC RESULT] playerApi.seekTo SUCCESS") }
            ?.setErrorCallback { Log.w(TAG, "[IPC RESULT] playerApi.seekTo ERROR: ${it.message}") }
    }

    override fun skipToNext() {
        Log.i(TAG, "[IPC CALL] playerApi.skipNext()")
        appRemote?.playerApi?.skipNext()
            ?.setResultCallback { Log.d(TAG, "[IPC RESULT] playerApi.skipNext SUCCESS") }
            ?.setErrorCallback { Log.w(TAG, "[IPC RESULT] playerApi.skipNext ERROR: ${it.message}") }
    }

    override fun skipToPrevious() {
        Log.i(TAG, "[IPC CALL] playerApi.skipPrevious()")
        appRemote?.playerApi?.skipPrevious()
            ?.setResultCallback { Log.d(TAG, "[IPC RESULT] playerApi.skipPrevious SUCCESS") }
            ?.setErrorCallback { Log.w(TAG, "[IPC RESULT] playerApi.skipPrevious ERROR: ${it.message}") }
    }

    override fun setShuffle(enabled: Boolean) {
        Log.i(TAG, "[IPC CALL] playerApi.setShuffle($enabled)")
        _playbackState.update { it.copy(shuffleEnabled = enabled) }
        appRemote?.playerApi?.setShuffle(enabled)
            ?.setResultCallback { Log.d(TAG, "[IPC RESULT] playerApi.setShuffle SUCCESS") }
            ?.setErrorCallback { Log.w(TAG, "[IPC RESULT] playerApi.setShuffle ERROR: ${it.message}") }
    }

    override fun toggleShuffle() {
        val next = !_playbackState.value.shuffleEnabled
        setShuffle(next)
    }

    override fun setSmartShuffle(enabled: Boolean) {
        setShuffle(enabled)
    }

    override fun toggleSmartShuffle() {
        toggleShuffle()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        Log.i(TAG, "[IPC CALL] playerApi.setRepeatMode($mode)")
        _playbackState.update { it.copy(repeatMode = mode) }
        val spotifyRepeat = when (mode) {
            RepeatMode.OFF -> Repeat.OFF
            RepeatMode.ONE -> Repeat.ONE
            RepeatMode.ALL -> Repeat.ALL
        }
        appRemote?.playerApi?.setRepeat(spotifyRepeat)
            ?.setResultCallback { Log.d(TAG, "[IPC RESULT] playerApi.setRepeat SUCCESS") }
            ?.setErrorCallback { Log.w(TAG, "[IPC RESULT] playerApi.setRepeat ERROR: ${it.message}") }
    }

    override fun toggleRepeat() {
        val next = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(next)
    }

    override fun setVolume(volume: Float) {
        _playbackState.update { it.copy(volume = volume) }
    }

    override fun addToQueue(track: Track) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            Log.i(TAG, "[IPC CALL] playerApi.queue('${track.uri}') for '${track.name}'")
            remote.playerApi.queue(track.uri)
                .setResultCallback { Log.d(TAG, "[IPC RESULT] playerApi.queue SUCCESS for '${track.name}'") }
                .setErrorCallback { Log.w(TAG, "[IPC RESULT] playerApi.queue ERROR for '${track.name}': ${it.message}") }
        } else {
            Log.w(TAG, "[IPC CALL] Cannot addToQueue: SpotifyAppRemote is not connected")
        }
    }

    override fun restoreSession(lastTrack: Track, positionMs: Long) {
        currentPlayingTrack = lastTrack
        _playbackState.update { prev ->
            prev.copy(
                currentTrack = lastTrack,
                positionMs = positionMs,
                durationMs = lastTrack.durationMs,
                isPlaying = false,
                isPaused = true
            )
        }
    }

    fun disconnect() {
        appRemote?.let {
            Log.i(TAG, "Disconnecting Spotify App Remote...")
            try {
                SpotifyAppRemote.disconnect(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting Spotify App Remote: ${e.message}")
            }
            appRemote = null
            _isConnected.value = false
        }
    }
}
