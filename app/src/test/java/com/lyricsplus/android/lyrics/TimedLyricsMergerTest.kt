package com.lyricsplus.android.lyrics

import com.lyricsplus.android.data.LyricsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimedLyricsMergerTest {
    @Test
    fun mergeTranslationUsesNearestLineWhenTranslationStartsAfterBaseLine() {
        val base = listOf(
            LyricsLine(25_820L, "I miss Long Beach and I miss you babe"),
            LyricsLine(31_370L, "I miss dancing with you the most of all")
        )
        val translation = listOf(
            LyricsLine(25_960L, "追忆着长滩 也追忆着你"),
            LyricsLine(31_630L, "最怀念不过 与你起舞翩翩")
        )

        val merged = TimedLyricsMerger.mergeTranslation(base, translation)

        assertEquals("追忆着长滩 也追忆着你", merged[0].translation)
        assertEquals("最怀念不过 与你起舞翩翩", merged[1].translation)
    }

    @Test
    fun mergeTranslationDoesNotReusePreviousTranslationForNextLine() {
        val base = listOf(
            LyricsLine(1_000L, "first"),
            LyricsLine(5_000L, "second")
        )
        val translation = listOf(
            LyricsLine(1_100L, "第一行")
        )

        val merged = TimedLyricsMerger.mergeTranslation(base, translation)

        assertEquals("第一行", merged[0].translation)
        assertNull(merged[1].translation)
    }
}
