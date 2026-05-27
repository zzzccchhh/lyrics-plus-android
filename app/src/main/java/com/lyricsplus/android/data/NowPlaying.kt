package com.lyricsplus.android.data

data class NowPlaying(
    val spotifyUri: String? = null,
    val track: String = "",
    val artist: String = "",
    val album: String = "",
    val durationSeconds: Int = 0,
    val backgroundStart: String? = null,
    val backgroundEnd: String? = null
) {
    val hasTrack: Boolean
        get() = track.isNotBlank() || artist.isNotBlank()
}
