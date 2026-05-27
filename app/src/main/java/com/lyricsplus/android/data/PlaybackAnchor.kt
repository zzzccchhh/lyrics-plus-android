package com.lyricsplus.android.data

data class PlaybackAnchor(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val capturedElapsedMs: Long = 0,
    val isAccurate: Boolean = true
)
