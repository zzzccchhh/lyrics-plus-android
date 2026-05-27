package com.lyricsplus.android.spotify

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.lyricsplus.android.data.NowPlaying
import com.lyricsplus.android.data.PlaybackAnchor

data class SpotifyMediaSnapshot(
    val nowPlaying: NowPlaying?,
    val playback: PlaybackAnchor?,
    val source: String
)

class SpotifyMediaSessionReader(private val context: Context) {
    private val notificationListener = ComponentName(context, LyricsNotificationListenerService::class.java)

    fun readSnapshot(): SpotifyMediaSnapshot? {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val controller = runCatching {
            manager.getActiveSessions(notificationListener)
                .firstOrNull { it.packageName == SpotifyBroadcasts.PACKAGE_NAME }
        }.getOrNull()

        if (controller != null) {
            return SpotifyMediaSnapshot(
                nowPlaying = controller.toNowPlaying(),
                playback = controller.toPlaybackAnchor(),
                source = "active-sessions"
            )
        }

        return LyricsNotificationListenerService.latestSnapshot
    }

    private fun MediaController.toNowPlaying(): NowPlaying? {
        val metadata = metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val palette = AlbumArtPaletteExtractor.fromBitmap(
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        )
        if (title.isBlank() && artist.isBlank()) return null

        return NowPlaying(
            track = title,
            artist = artist,
            album = album,
            durationSeconds = ((durationMs + 500) / 1000).toInt(),
            backgroundStart = palette?.start,
            backgroundEnd = palette?.end
        )
    }

    private fun MediaController.toPlaybackAnchor(): PlaybackAnchor? {
        val state = playbackState ?: return null
        val capturedElapsedMs = SystemClock.elapsedRealtime()
        
        // Use a safe lastPositionUpdateTime. Some devices report 0 initially.
        val lastUpdate = if (state.lastPositionUpdateTime > 0) state.lastPositionUpdateTime else capturedElapsedMs
        
        val positionMs = if (state.state == PlaybackState.STATE_PLAYING) {
            state.position + ((capturedElapsedMs - lastUpdate) * state.playbackSpeed).toLong()
        } else {
            state.position
        }

        val isAccurate = !(state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime <= 0)

        return PlaybackAnchor(
            isPlaying = state.state == PlaybackState.STATE_PLAYING,
            positionMs = positionMs.coerceAtLeast(0),
            capturedElapsedMs = capturedElapsedMs,
            isAccurate = isAccurate
        )
    }
}
