package com.lyricsplus.android.data

import android.os.SystemClock

data class PlaybackAnchor(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val capturedElapsedMs: Long = 0,
    val isAccurate: Boolean = true
) {
    fun currentPositionMs(): Long {
        if (!isPlaying || capturedElapsedMs <= 0L) {
            return positionMs
        }
        val elapsed = SystemClock.elapsedRealtime() - capturedElapsedMs
        return positionMs + elapsed.coerceAtLeast(0L)
    }
}
