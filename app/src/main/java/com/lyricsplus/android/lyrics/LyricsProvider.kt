package com.lyricsplus.android.lyrics

import android.content.Context
import com.lyricsplus.android.data.LyricsLine
import com.lyricsplus.android.data.NowPlaying
import com.lyricsplus.android.data.LyricsSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LyricsProvider(
    context: Context,
    private val qqMusicClient: QQMusicClient = QQMusicClient(),
    private val neteaseClient: NeteaseClient = NeteaseClient(),
    private val lrclibClient: LrclibClient = LrclibClient(),
    private val japaneseReader: JapaneseReadingConverter = JapaneseReadingConverter()
) {
    private val cacheDb = LyricsCacheDatabase(context)
    
    // In-memory cache for temporary source switching (秒切)
    // Key: trackKey|SourceName -> CachedLyricsResult
    private val inMemoryCache = ConcurrentHashMap<String, CachedLyricsResult>()

    init {
        HttpClient.initialize(context)
    }

    fun preWarm() {
        japaneseReader.preWarm()
    }

    fun clearCache(track: NowPlaying) {
        val trackKey = listOf(track.track, track.artist, track.album, track.durationSeconds).joinToString("|")
        cacheDb.deleteLyrics(trackKey)
        // Clear in-memory cache for this track key
        inMemoryCache.keys.filter { it.startsWith("$trackKey|") }.forEach { inMemoryCache.remove(it) }
    }

    fun saveToCache(track: NowPlaying, lyrics: List<LyricsLine>, source: String) {
        val trackKey = listOf(track.track, track.artist, track.album, track.durationSeconds).joinToString("|")
        cacheDb.saveLyrics(trackKey, lyrics, source)
    }

    suspend fun findSyncedLyrics(
        track: NowPlaying,
        preferredSource: String? = null
    ): Result<CachedLyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val trackKey = listOf(track.track, track.artist, track.album, track.durationSeconds).joinToString("|")
            
            if (preferredSource != null) {
                return@runCatching findSyncedLyricsForSource(track, preferredSource).getOrThrow()
            }

            // Check SQLite database cache first
            val cached = cacheDb.getLyrics(trackKey)
            if (cached != null) {
                // Return cached version with perfect score to avoid background override
                return@runCatching cached.copy(score = 140)
            }

            // Clear in-memory cache for this track key on starting fresh
            inMemoryCache.keys.filter { it.startsWith("$trackKey|") }.forEach { inMemoryCache.remove(it) }

            var resolvedSource = "网易云音乐"
            val netease = findSyncedLyricsForSource(track, "网易云音乐")
            val resolved = netease.getOrElse { neteaseErr ->
                resolvedSource = "QQ音乐"
                val qq = findSyncedLyricsForSource(track, "QQ音乐")
                qq.getOrElse { qqErr ->
                    resolvedSource = "LRCLIB"
                    val lrclib = findSyncedLyricsForSource(track, "LRCLIB")
                    lrclib.getOrElse { lrclibErr ->
                        if (neteaseErr is java.io.IOException || qqErr is java.io.IOException || lrclibErr is java.io.IOException) {
                            throw (neteaseErr as? java.io.IOException) ?: (qqErr as? java.io.IOException) ?: (lrclibErr as? java.io.IOException) ?: neteaseErr
                        } else {
                            val instrumental = listOf(LyricsLine(0L, "纯音乐 / 无歌词"))
                            cacheDb.saveLyrics(trackKey, instrumental, "纯音乐")
                            val result = CachedLyricsResult(instrumental, "纯音乐", score = 0)
                            inMemoryCache["$trackKey|纯音乐"] = result
                            return@runCatching result
                        }
                    }
                }
            }

            // Cache the first matched result to SQLite database
            cacheDb.saveLyrics(trackKey, resolved.lyrics, resolvedSource)
            resolved
        }
    }

    suspend fun findSyncedLyricsForSource(
        track: NowPlaying,
        sourceName: String
    ): Result<CachedLyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val trackKey = listOf(track.track, track.artist, track.album, track.durationSeconds).joinToString("|")
            val memKey = "$trackKey|$sourceName"
            
            val memCached = inMemoryCache[memKey]
            if (memCached != null) {
                return@runCatching memCached
            }

            val searchResult = when (sourceName) {
                "QQ音乐" -> qqMusicClient.findSyncedLyrics(track).getOrThrow()
                "网易云音乐" -> neteaseClient.findSyncedLyrics(track).getOrThrow()
                "LRCLIB" -> lrclibClient.findSyncedLyrics(track).getOrThrow()
                else -> error("Unknown source: $sourceName")
            }

            val base = searchResult.lyrics
            val hasKana = base.any { line -> line.text.hasJapaneseKana() }

            val processed = base.map { line ->
                val reading = if (hasKana) japaneseReader.readingFor(line.text) else null
                line.copy(reading = reading)
            }

            val result = CachedLyricsResult(processed, sourceName, searchResult.score)
            inMemoryCache[memKey] = result
            result
        }
    }

    private fun String.hasJapaneseKana(): Boolean =
        any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' }

    fun isCached(track: NowPlaying): Boolean {
        val trackKey = listOf(track.track, track.artist, track.album, track.durationSeconds).joinToString("|")
        if (inMemoryCache.keys.any { it.startsWith("$trackKey|") }) return true
        return cacheDb.hasLyrics(trackKey)
    }

    fun isCachedForSource(track: NowPlaying, sourceName: String): Boolean {
        val trackKey = listOf(track.track, track.artist, track.album, track.durationSeconds).joinToString("|")
        return inMemoryCache.containsKey("$trackKey|$sourceName")
    }

    companion object {
        @Volatile
        private var instance: LyricsProvider? = null

        fun getInstance(context: Context): LyricsProvider {
            return instance ?: synchronized(this) {
                instance ?: LyricsProvider(context.applicationContext).also { instance = it }
            }
        }
    }
}
