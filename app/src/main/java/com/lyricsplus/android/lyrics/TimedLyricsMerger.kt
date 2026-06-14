package com.lyricsplus.android.lyrics

import com.lyricsplus.android.data.LyricsLine
import kotlin.math.abs

object TimedLyricsMerger {
    private const val DEFAULT_MAX_OFFSET_MS = 8_000L

    fun mergeTranslation(
        base: List<LyricsLine>,
        translation: List<LyricsLine>,
        maxOffsetMs: Long = DEFAULT_MAX_OFFSET_MS,
        lineFilter: (LyricsLine) -> Boolean = { true }
    ): List<LyricsLine> {
        if (base.isEmpty() || translation.isEmpty()) return base
        val matches = nearestMatchesByBaseIndex(base, translation, maxOffsetMs)
        return base.mapIndexed { index, line ->
            val matched = matches[index]
            if (matched != null && lineFilter(line)) {
                line.copy(translation = matched.text)
            } else {
                line
            }
        }
    }

    fun mergeReading(
        base: List<LyricsLine>,
        reading: List<LyricsLine>,
        maxOffsetMs: Long = DEFAULT_MAX_OFFSET_MS
    ): List<LyricsLine> {
        if (base.isEmpty() || reading.isEmpty()) return base
        val matches = nearestMatchesByBaseIndex(base, reading, maxOffsetMs)
        return base.mapIndexed { index, line ->
            val matched = matches[index]
            if (matched != null) {
                line.copy(reading = matched.text)
            } else {
                line
            }
        }
    }

    private fun nearestMatchesByBaseIndex(
        base: List<LyricsLine>,
        timedText: List<LyricsLine>,
        maxOffsetMs: Long
    ): Map<Int, LyricsLine> {
        val starts = LongArray(base.size) { index -> base[index].startTimeMs }
        val best = mutableMapOf<Int, TimedMatch>()

        timedText
            .filter { it.text.isNotBlank() }
            .sortedBy { it.startTimeMs }
            .forEach { timedLine ->
                val nearest = nearestBaseIndex(starts, timedLine.startTimeMs) ?: return@forEach
                if (nearest.offsetMs > maxOffsetMs) return@forEach

                val existing = best[nearest.index]
                if (existing == null || nearest.offsetMs < existing.offsetMs) {
                    best[nearest.index] = TimedMatch(timedLine, nearest.offsetMs)
                }
            }

        return best.mapValues { it.value.line }
    }

    private fun nearestBaseIndex(starts: LongArray, timeMs: Long): NearestBase? {
        if (starts.isEmpty()) return null

        val insertionPoint = lowerBound(starts, timeMs)
        val previousIndex = insertionPoint - 1
        val nextIndex = insertionPoint

        val previous = previousIndex.takeIf { it >= 0 }?.let { index ->
            NearestBase(index, abs(timeMs - starts[index]))
        }
        val next = nextIndex.takeIf { it < starts.size }?.let { index ->
            NearestBase(index, abs(starts[index] - timeMs))
        }

        return when {
            previous == null -> next
            next == null -> previous
            previous.offsetMs <= next.offsetMs -> previous
            else -> next
        }
    }

    private fun lowerBound(values: LongArray, target: Long): Int {
        var low = 0
        var high = values.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (values[mid] < target) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    private data class NearestBase(
        val index: Int,
        val offsetMs: Long
    )

    private data class TimedMatch(
        val line: LyricsLine,
        val offsetMs: Long
    )
}
