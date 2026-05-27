package com.lyricsplus.android

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lyricsplus.android.data.LyricsLine
import com.lyricsplus.android.data.NowPlaying
import com.lyricsplus.android.data.PlaybackAnchor
import com.lyricsplus.android.lyrics.LyricsProvider
import com.lyricsplus.android.spotify.SpotifyMediaSnapshot
import com.lyricsplus.android.spotify.SpotifyBroadcasts
import com.lyricsplus.android.spotify.LyricsNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LyricsUiState(
    val nowPlaying: NowPlaying = NowPlaying(),
    val playback: PlaybackAnchor = PlaybackAnchor(),
    val lyrics: List<LyricsLine> = emptyList(),
    val isLoadingLyrics: Boolean = false,
    val message: String = "Open Spotify and play a song",
    val lastBroadcastAction: String? = null,
    val playbackSource: String = "none",
    val lyricsOffsetMs: Long = 0L,
    val animationDuration: Int = 45,
    val showRomaji: Boolean = true,
    val animationRunning: Boolean = false
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val lyricsProvider: LyricsProvider = LyricsProvider(application)

    private val _uiState = MutableStateFlow(LyricsUiState())
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    private var lyricsRequestKey: String? = null
    private var lastAccurateUpdateMs: Long = 0

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            lyricsProvider.preWarm()
        }

        viewModelScope.launch {
            com.lyricsplus.android.spotify.LyricsNotificationListenerService.snapshotFlow.collect { snapshot ->
                if (snapshot != null) {
                    onMediaSnapshot(snapshot)
                } else {
                    onMediaSessionUnavailable()
                }
            }
        }
    }

    fun onMediaSessionUnavailable() {
        _uiState.update { state ->
            if (state.playback.isPlaying) {
                state.copy(
                    playback = state.playback.copy(isPlaying = false),
                    message = "Spotify disconnected"
                )
            } else {
                state
            }
        }
    }

    fun onSpotifyIntent(intent: Intent) {
        when (intent.action) {
            SpotifyBroadcasts.METADATA_CHANGED -> handleMetadata(intent)
            SpotifyBroadcasts.PLAYBACK_STATE_CHANGED -> {
                val now = SystemClock.elapsedRealtime()
                val hasRecentAccurateData = (now - lastAccurateUpdateMs) < 2000
                val isAtInitialState = _uiState.value.playback.positionMs == 0L
                
                // Trust broadcast ONLY if we have no data yet OR accurate data is stale
                if (isAtInitialState || !hasRecentAccurateData) {
                    handlePlayback(intent)
                }
            }
            SpotifyBroadcasts.QUEUE_CHANGED -> {
                _uiState.update {
                    it.copy(
                        message = "Queue changed",
                        lastBroadcastAction = SpotifyBroadcasts.QUEUE_CHANGED
                    )
                }
            }
        }
    }

    fun onMediaSnapshot(snapshot: SpotifyMediaSnapshot) {
        val nextTrack = snapshot.nowPlaying
        val nextPlayback = snapshot.playback

        if (nextTrack == null && nextPlayback == null) return

        if (nextPlayback?.isAccurate == true && (snapshot.source == "active-sessions" || snapshot.source == "notification-token")) {
            lastAccurateUpdateMs = SystemClock.elapsedRealtime()
        }

        _uiState.update { state ->
            val mergedTrack = nextTrack?.withPaletteFallback(state.nowPlaying)
            val updatedPlayback = if (nextPlayback != null) {
                if (nextPlayback.isAccurate || state.playback.positionMs == 0L || !state.playback.isPlaying) {
                    nextPlayback
                } else {
                    state.playback
                }
            } else {
                state.playback
            }

            val isNewTrack = mergedTrack?.hasTrack == true && mergedTrack.requestKey() != state.nowPlaying.requestKey()

            state.copy(
                nowPlaying = mergedTrack ?: state.nowPlaying,
                playback = updatedPlayback,
                lyrics = if (isNewTrack) emptyList() else state.lyrics,
                lyricsOffsetMs = if (isNewTrack) 0L else state.lyricsOffsetMs,
                isLoadingLyrics = if (mergedTrack?.hasTrack == true && shouldFetchLyrics(mergedTrack)) true else state.isLoadingLyrics,
                message = when {
                    mergedTrack?.hasTrack == true -> "Media session synced: ${snapshot.source}"
                    nextPlayback?.isPlaying == true -> "Playing"
                    nextPlayback != null -> "Paused"
                    else -> state.message
                },
                playbackSource = snapshot.source
            )
        }

        val mergedTrack = nextTrack?.withPaletteFallback(_uiState.value.nowPlaying)
        if (mergedTrack?.hasTrack == true && shouldFetchLyrics(mergedTrack)) {
            fetchLyrics(mergedTrack)
        }
    }

    private fun handleMetadata(intent: Intent) {
        val track = NowPlaying(
            spotifyUri = intent.getStringExtra(SpotifyBroadcasts.EXTRA_ID),
            track = intent.getStringExtra(SpotifyBroadcasts.EXTRA_TRACK).orEmpty(),
            artist = intent.getStringExtra(SpotifyBroadcasts.EXTRA_ARTIST).orEmpty(),
            album = intent.getStringExtra(SpotifyBroadcasts.EXTRA_ALBUM).orEmpty(),
            durationSeconds = (intent.getIntExtra(SpotifyBroadcasts.EXTRA_LENGTH, 0) + 500) / 1000
        )
        val shouldFetch = track.hasTrack && shouldFetchLyrics(track)

        _uiState.update {
            val mergedTrack = track.withPaletteFallback(it.nowPlaying)
            it.copy(
                nowPlaying = mergedTrack,
                lyrics = if (shouldFetch) emptyList() else it.lyrics,
                isLoadingLyrics = shouldFetch,
                message = when {
                    shouldFetch -> "Finding synced lyrics"
                    track.hasTrack -> "Track metadata updated"
                    else -> "Waiting for track metadata"
                },
                lastBroadcastAction = SpotifyBroadcasts.METADATA_CHANGED
            )
        }

        if (shouldFetch) {
            fetchLyrics(track)
        }
    }

    private fun handlePlayback(intent: Intent) {
        val timeSent = intent.getLongExtra("timeSent", 0L)
        val delay = if (timeSent > 0L) {
            (System.currentTimeMillis() - timeSent).coerceAtLeast(0L)
        } else {
            0L
        }
        val capturedElapsedMs = SystemClock.elapsedRealtime() - delay

        val playback = PlaybackAnchor(
            isPlaying = intent.getBooleanExtra(SpotifyBroadcasts.EXTRA_PLAYING, false),
            positionMs = intent.getIntExtra(SpotifyBroadcasts.EXTRA_PLAYBACK_POSITION, 0).toLong(),
            capturedElapsedMs = capturedElapsedMs,
            isAccurate = true
        )

        _uiState.update {
            it.copy(
                playback = playback,
                message = if (playback.isPlaying) "Playing" else "Paused",
                lastBroadcastAction = SpotifyBroadcasts.PLAYBACK_STATE_CHANGED,
                playbackSource = "spotify-broadcast"
            )
        }
    }

    private fun fetchLyrics(track: NowPlaying) {
        val requestKey = track.requestKey()
        lyricsRequestKey = requestKey

        viewModelScope.launch {
            val result = lyricsProvider.findSyncedLyrics(track)
            if (lyricsRequestKey != requestKey) return@launch

            _uiState.update { state ->
                result.fold(
                    onSuccess = { lyrics ->
                        state.copy(
                            lyrics = lyrics,
                            isLoadingLyrics = false,
                            message = "Synced lyrics loaded"
                        )
                    },
                    onFailure = { error ->
                        // Show "instrumental" as a lyric line so the WebView
                        // renders it with the colourful background instead of
                        // falling back to the black EmptyOverlay.
                        val instrumentalLine = listOf(
                            LyricsLine(
                                startTimeMs = 0L,
                                text = "纯音乐 / 无歌词",
                                translation = "",
                                reading = null
                            )
                        )
                        state.copy(
                            lyrics = instrumentalLine,
                            isLoadingLyrics = false,
                            message = error.message ?: "No synced lyrics found"
                        )
                    }
                )
            }
        }
    }

    private fun shouldFetchLyrics(track: NowPlaying): Boolean {
        return track.requestKey() != lyricsRequestKey
    }

    private fun NowPlaying.requestKey(): String =
        listOf(track, artist, album, durationSeconds).joinToString("|")

    private fun NowPlaying.withPaletteFallback(previous: NowPlaying): NowPlaying =
        if (requestKey() == previous.requestKey()) {
            copy(
                backgroundStart = backgroundStart ?: previous.backgroundStart,
                backgroundEnd = backgroundEnd ?: previous.backgroundEnd
            )
        } else {
            this
        }

    fun adjustOffset(deltaMs: Long) {
        _uiState.update { it.copy(lyricsOffsetMs = it.lyricsOffsetMs + deltaMs) }
    }

    fun adjustAnimationSpeed(deltaSeconds: Int) {
        _uiState.update {
            val next = (it.animationDuration + deltaSeconds).coerceIn(10, 120)
            it.copy(animationDuration = next)
        }
    }

    fun toggleRomaji() {
        _uiState.update { it.copy(showRomaji = !it.showRomaji) }
    }

    fun forceSyncTime() {
        viewModelScope.launch {
            val snapshot = com.lyricsplus.android.spotify.LyricsNotificationListenerService.snapshotFlow.value
            if (snapshot != null) {
                _uiState.update { it.copy(lyricsOffsetMs = 0L) }
                onMediaSnapshot(snapshot)
                android.widget.Toast.makeText(getApplication(), "歌词时间已同步重置", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(getApplication(), "无法同步：未找到活动的媒体会话", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleAnimation() {
        _uiState.update { it.copy(animationRunning = !it.animationRunning) }
    }

    private fun getSpotifyController(): MediaController? {
        val manager = getApplication<Application>().getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        val component = ComponentName(getApplication(), LyricsNotificationListenerService::class.java)
        return runCatching {
            manager.getActiveSessions(component)
                .firstOrNull { it.packageName == SpotifyBroadcasts.PACKAGE_NAME }
        }.getOrNull()
    }

    fun togglePlayback() {
        val controller = getSpotifyController()
        if (controller != null) {
            val state = controller.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } else {
            android.widget.Toast.makeText(getApplication(), "无法控制：请确保已启动 Spotify", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun skipToNext() {
        val controller = getSpotifyController()
        if (controller != null) {
            controller.transportControls.skipToNext()
        } else {
            android.widget.Toast.makeText(getApplication(), "无法控制：请确保已启动 Spotify", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
