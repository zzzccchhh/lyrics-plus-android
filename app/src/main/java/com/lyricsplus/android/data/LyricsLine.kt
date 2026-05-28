package com.lyricsplus.android.data

data class LyricsLine(
    val startTimeMs: Long,
    val text: String,
    val translation: String? = null,
    val reading: String? = null
)

data class LyricsSearchResult(
    val lyrics: List<LyricsLine>,
    val score: Int
)
