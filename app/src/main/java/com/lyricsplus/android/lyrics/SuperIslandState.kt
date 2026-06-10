package com.lyricsplus.android.lyrics

data class SuperIslandState(
    val title: String,
    val artist: String,
    val lyric: String,
    val fullLyric: String,
    val leftLyric: String? = null,
    val rightLyric: String? = null,
    val progressPercent: Int,
    val isPlaying: Boolean,
    val accentColor: Int,
    val mediaPackage: String
)
