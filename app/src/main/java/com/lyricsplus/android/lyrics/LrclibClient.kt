package com.lyricsplus.android.lyrics

import com.lyricsplus.android.data.LyricsLine
import com.lyricsplus.android.data.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs

class LrclibClient {
    suspend fun findSyncedLyrics(track: NowPlaying): Result<List<LyricsLine>> = withContext(Dispatchers.IO) {
        runCatching {
            fetchExact(track) ?: searchBestMatch(track) ?: error("No synced lyrics found on LRCLIB")
        }
    }

    private fun fetchExact(track: NowPlaying): List<LyricsLine>? {
        val query = buildString {
            append("https://lrclib.net/api/get")
            append("?track_name=").append(track.track.urlEncode())
            append("&artist_name=").append(track.artist.urlEncode())
            if (track.album.isNotBlank()) append("&album_name=").append(track.album.urlEncode())
            if (track.durationSeconds > 0) append("&duration=").append(track.durationSeconds)
        }

        val response = request(query)
        if (response.code !in 200..299) return null

        val obj = JSONObject(response.body)
        if (obj.optBoolean("instrumental", false)) {
            return listOf(LyricsLine(0L, "♪ 纯音乐 ♪"))
        }

        val synced = obj.optString("syncedLyrics")
        return parseSynced(synced)
    }

    private fun searchBestMatch(track: NowPlaying): List<LyricsLine>? {
        val query = buildString {
            append("https://lrclib.net/api/search")
            append("?track_name=").append(track.track.urlEncode())
            append("&artist_name=").append(track.artist.urlEncode())
        }

        val response = request(query)
        if (response.code !in 200..299) return null

        val results = JSONArray(response.body)
        val candidates = (0 until results.length())
            .map { results.getJSONObject(it) }
            .filter { it.optString("syncedLyrics").isNotBlank() || it.optBoolean("instrumental", false) }

        val best = candidates.minByOrNull { result ->
            if (track.durationSeconds > 0) {
                abs(result.optInt("duration", track.durationSeconds) - track.durationSeconds)
            } else {
                0
            }
        } ?: return null

        if (best.optBoolean("instrumental", false)) {
            return listOf(LyricsLine(0L, "♪ 纯音乐 ♪"))
        }

        return parseSynced(best.optString("syncedLyrics"))
    }

    private fun parseSynced(syncedLyrics: String): List<LyricsLine>? {
        if (syncedLyrics.isBlank()) return null
        val parsed = LrcParser.parse(syncedLyrics)
        return if (parsed.isEmpty()) null else parsed
    }

    private fun request(url: String): HttpResponse {
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "LyricsPlusAndroid/0.1")
            .build()

        return HttpClient.okHttpClient.newCall(request).execute().use { response ->
            HttpResponse(
                code = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String
    )

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
