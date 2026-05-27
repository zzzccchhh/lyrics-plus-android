package com.lyricsplus.android.lyrics

import android.content.Context
import com.lyricsplus.android.data.LyricsLine
import com.lyricsplus.android.data.NowPlaying

class LyricsProvider(
    context: Context,
    private val neteaseClient: NeteaseClient = NeteaseClient(),
    private val lrclibClient: LrclibClient = LrclibClient(),
    private val japaneseReader: JapaneseReadingConverter = JapaneseReadingConverter()
) {
    private val cacheDb = LyricsCacheDatabase(context)

    fun preWarm() {
        japaneseReader.preWarm()
    }

    suspend fun findSyncedLyrics(track: NowPlaying): Result<List<LyricsLine>> {
        return runCatching {
            val trackKey = listOf(track.track, track.artist, track.album, track.durationSeconds).joinToString("|")
            val cached = cacheDb.getLyrics(trackKey)
            if (cached != null) {
                return@runCatching cached
            }

            val netease = neteaseClient.findSyncedLyrics(track)
            val base = netease.getOrElse {
                lrclibClient.findSyncedLyrics(track).getOrElse {
                    // Both APIs failed — cache an instrumental placeholder
                    // so we don't re-fetch every time
                    val instrumental = listOf(LyricsLine(0L, "纯音乐 / 无歌词"))
                    cacheDb.saveLyrics(trackKey, instrumental)
                    return@runCatching instrumental
                }
            }

            val hasKana = base.any { line -> line.text.hasJapaneseKana() }

            val processed = base.map { line ->
                val reading = if (hasKana) japaneseReader.readingFor(line.text) else null
                line.copy(reading = reading)
            }

            cacheDb.saveLyrics(trackKey, processed)
            processed
        }
    }

    private fun String.hasJapaneseKana(): Boolean =
        any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' }
}
