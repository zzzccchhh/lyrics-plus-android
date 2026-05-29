package com.lyricsplus.android.lyrics

import com.lyricsplus.android.data.LyricsLine

object LrcParser {
    private val timestampRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val yrcTimestampRegex = Regex("^\\[(\\d+),(\\d+)]")

    fun parse(lrc: String): List<LyricsLine> {
        if (lrc.isBlank()) return emptyList()

        val isYrc = lrc.lineSequence().any { yrcTimestampRegex.containsMatchIn(it) }

        if (isYrc) {
            return lrc.lineSequence()
                .mapNotNull { rawLine ->
                    val match = yrcTimestampRegex.find(rawLine) ?: return@mapNotNull null
                    val startTime = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                    val text = rawLine.substring(match.range.last + 1).trim().ifBlank { "♪" }
                    LyricsLine(startTime, text)
                }
                .sortedBy { it.startTimeMs }
                .distinctBy { it.startTimeMs to it.text }
                .toList()
        }

        return lrc.lineSequence()
            .flatMap { rawLine ->
                val matches = timestampRegex.findAll(rawLine).toList()
                if (matches.isEmpty()) {
                    emptySequence()
                } else {
                    val text = rawLine.replace(timestampRegex, "").trim().ifBlank { "♪" }
                    matches.asSequence().mapNotNull { match ->
                        val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                        val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                        val fraction = match.groupValues.getOrNull(3).orEmpty()
                        val millis = when (fraction.length) {
                            0 -> 0
                            1 -> fraction.toLong() * 100
                            2 -> fraction.toLong() * 10
                            else -> fraction.take(3).toLong()
                        }
                        LyricsLine(minutes * 60_000 + seconds * 1_000 + millis, text)
                    }
                }
            }
            .sortedBy { it.startTimeMs }
            .distinctBy { it.startTimeMs to it.text }
            .toList()
    }
}
