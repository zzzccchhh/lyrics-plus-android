package com.lyricsplus.android.lyrics

import android.content.Context
import com.lyricsplus.android.data.LyricsLine
import com.lyricsplus.android.data.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsProvider(
    context: Context,
    private val neteaseClient: NeteaseClient = NeteaseClient(),
    private val lrclibClient: LrclibClient = LrclibClient(),
    private val japaneseReader: JapaneseReadingConverter = JapaneseReadingConverter()
) {
    private val cacheDb = LyricsCacheDatabase(context)

    init {
        HttpClient.initialize(context)
    }

    fun preWarm() {
        japaneseReader.preWarm()
    }

    suspend fun findSyncedLyrics(track: NowPlaying): Result<List<LyricsLine>> = withContext(Dispatchers.IO) {
        runCatching {
            val trackKey = listOf(track.track, track.artist, track.album, track.durationSeconds).joinToString("|")
            val cached = cacheDb.getLyrics(trackKey)
            if (cached != null) {
                return@runCatching cached
            }

            val netease = neteaseClient.findSyncedLyrics(track)
            val base = netease.getOrElse { neteaseErr ->
                val lrclib = lrclibClient.findSyncedLyrics(track)
                lrclib.getOrElse { lrclibErr ->
                    // Both APIs failed. Check if either failure was due to network anomalies (IOException)
                    if (neteaseErr is java.io.IOException || lrclibErr is java.io.IOException) {
                        // Network error! Throw the exception instead of caching an instrumental placeholder
                        throw neteaseErr ?: lrclibErr
                    } else {
                        // Both completed search requests but logically returned no results.
                        // Cache this genuine "no lyrics" state so we don't spam the servers.
                        val instrumental = listOf(LyricsLine(0L, "纯音乐 / 无歌词"))
                        cacheDb.saveLyrics(trackKey, instrumental)
                        return@runCatching instrumental
                    }
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
