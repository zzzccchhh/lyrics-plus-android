package com.lyricsplus.android.spotify

import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lyricsplus.android.data.NowPlaying
import com.lyricsplus.android.data.PlaybackAnchor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LyricsNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshFromActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == SpotifyBroadcasts.PACKAGE_NAME) {
            latestSnapshot = sbn.toSpotifySnapshot()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == SpotifyBroadcasts.PACKAGE_NAME) {
            refreshFromActiveNotifications()
        }
    }

    private fun refreshFromActiveNotifications() {
        latestSnapshot = activeNotifications
            ?.firstOrNull { it.packageName == SpotifyBroadcasts.PACKAGE_NAME }
            ?.toSpotifySnapshot()
    }

    private fun StatusBarNotification.toSpotifySnapshot(): SpotifyMediaSnapshot {
        val token = notification.mediaSessionToken()
        val controller = token?.let { MediaController(this@LyricsNotificationListenerService, it) }
        val extras = notification.extras

        val metadata = controller?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val palette = AlbumArtPaletteExtractor.fromBitmap(
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        )

        return SpotifyMediaSnapshot(
            nowPlaying = if (title.isNotBlank() || artist.isNotBlank()) {
                NowPlaying(
                    track = title,
                    artist = artist,
                    album = album,
                    durationSeconds = ((durationMs + 500) / 1000).toInt(),
                    backgroundStart = palette?.start,
                    backgroundEnd = palette?.end
                )
            } else {
                null
            },
            playback = controller?.playbackState?.toPlaybackAnchor(),
            source = "notification-token"
        )
    }

    @Suppress("DEPRECATION")
    private fun Notification.mediaSessionToken(): MediaSession.Token? =
        extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)

    private fun PlaybackState.toPlaybackAnchor(): PlaybackAnchor {
        val capturedElapsedMs = SystemClock.elapsedRealtime()
        
        // Use a safe lastPositionUpdateTime. Some devices report 0 initially.
        val lastUpdate = if (lastPositionUpdateTime > 0) lastPositionUpdateTime else capturedElapsedMs

        val positionMs = if (state == PlaybackState.STATE_PLAYING) {
            position + ((capturedElapsedMs - lastUpdate) * playbackSpeed).toLong()
        } else {
            position
        }

        val isAccurate = !(state == PlaybackState.STATE_PLAYING && lastPositionUpdateTime <= 0)

        return PlaybackAnchor(
            isPlaying = state == PlaybackState.STATE_PLAYING,
            positionMs = positionMs.coerceAtLeast(0),
            capturedElapsedMs = capturedElapsedMs,
            isAccurate = isAccurate
        )
    }

    companion object {
        private val _snapshotFlow = MutableStateFlow<SpotifyMediaSnapshot?>(null)
        val snapshotFlow: StateFlow<SpotifyMediaSnapshot?> = _snapshotFlow.asStateFlow()

        var latestSnapshot: SpotifyMediaSnapshot?
            get() = _snapshotFlow.value
            set(value) {
                _snapshotFlow.value = value
            }
    }
}
