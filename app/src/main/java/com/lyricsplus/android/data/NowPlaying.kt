package com.lyricsplus.android.data

import android.graphics.Bitmap
import com.lyricsplus.android.spotify.AlbumArtPalette

data class NowPlaying(
    val spotifyUri: String? = null,
    val track: String = "",
    val artist: String = "",
    val album: String = "",
    val mediaPackage: String = "",
    val durationSeconds: Int = 0,
    val backgroundStart: String? = null,
    val backgroundEnd: String? = null,
    val backgroundAccent: String? = null,
    val albumArt: Bitmap? = null,
    val colorStyleIndex: Int = 0,
    val paletteTemplates: List<AlbumArtPalette> = emptyList()
) {
    val hasTrack: Boolean
        get() = track.isNotBlank() || artist.isNotBlank()
}
