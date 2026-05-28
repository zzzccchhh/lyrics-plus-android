package com.lyricsplus.android.lyrics

import com.lyricsplus.android.data.LyricsLine
import com.lyricsplus.android.data.NowPlaying
import com.lyricsplus.android.data.LyricsSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs

class QQMusicClient {
    suspend fun findSyncedLyrics(track: NowPlaying): Result<LyricsSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val searchResult = searchSongMid(track) ?: error("Cannot find QQ Music track")
            val lyricData = fetchLyrics(searchResult.mid) ?: error("Cannot find QQ Music lyrics")
            
            val lyricBase64 = lyricData.optString("lyric").orEmpty()
            val transBase64 = lyricData.optString("trans").orEmpty()
            
            if (lyricBase64.isBlank()) error("QQ Music lyrics were empty")

            val rawLyricEncoded = String(android.util.Base64.decode(lyricBase64, android.util.Base64.DEFAULT), Charsets.UTF_8)
            val rawTransEncoded = if (transBase64.isNotBlank()) {
                String(android.util.Base64.decode(transBase64, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } else ""

            val rawLyric = unescapeHtml(rawLyricEncoded)
            val rawTrans = unescapeHtml(rawTransEncoded)

            val synced = LrcParser.parse(rawLyric).ifEmpty { error("QQ Music synced lyrics empty") }
            val translation = LrcParser.parse(rawTrans)

            val merged = mergeTranslation(synced, translation)
            LyricsSearchResult(merged, searchResult.score)
        }
    }

    private fun unescapeHtml(text: String): String {
        if (text.isBlank()) return text
        val temp = text.replace("\n", "__NEWLINE_PLACEHOLDER__")
        val unescaped = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(temp, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(temp).toString()
        }
        return unescaped.replace("__NEWLINE_PLACEHOLDER__", "\n")
    }

    private fun searchSongMid(track: NowPlaying): QQMusicSearchResult? {
        val query = "${track.track} ${track.artist}"
        
        val paramJson = JSONObject()
            .put("query", query)
            .put("page_num", 1)
            .put("num_per_page", 10)
            .put("search_type", 0)

        val serviceJson = JSONObject()
            .put("method", "DoSearchForQQMusicDesktop")
            .put("module", "music.search.SearchCgiService")
            .put("param", paramJson)

        val payloadJson = JSONObject()
            .put("music.search.SearchCgiService", serviceJson)

        val response = requestPost("https://u.y.qq.com/cgi-bin/musicu.fcg", payloadJson.toString())
        if (response.code !in 200..299) return null

        val json = JSONObject(response.body)
        val songs = json.optJSONObject("music.search.SearchCgiService")
            ?.optJSONObject("data")
            ?.optJSONObject("body")
            ?.optJSONObject("song")
            ?.optJSONArray("list")
            ?: return null

        var bestMid: String? = null
        var bestScore = -1

        val normalizedTitle = track.track.lowercase().replace("\\s+".toRegex(), "")
        val normalizedArtist = track.artist.lowercase().replace("\\s+".toRegex(), "")
        val normalizedAlbum = track.album.lowercase().replace("\\s+".toRegex(), "")

        for (i in 0 until songs.length()) {
            val song = songs.getJSONObject(i)
            val name = song.optString("name").lowercase().replace("\\s+".toRegex(), "")
            val album = song.optJSONObject("album")?.optString("name").orEmpty().lowercase().replace("\\s+".toRegex(), "")
            
            val singersArray = song.optJSONArray("singer")
            val singers = StringBuilder()
            if (singersArray != null) {
                for (j in 0 until singersArray.length()) {
                    singers.append(singersArray.getJSONObject(j).optString("name")).append(" ")
                }
            }
            val artistStr = singers.toString().lowercase().replace("\\s+".toRegex(), "")
            
            val duration = song.optLong("interval")
            val expectedDuration = track.durationSeconds.toLong()
            val durationDiff = if (expectedDuration > 0) abs(expectedDuration - duration) else Long.MAX_VALUE

            var score = 0
            if (name == normalizedTitle) score += 50
            else if (normalizedTitle.isNotBlank() && (name.contains(normalizedTitle) || normalizedTitle.contains(name))) score += 20
            
            if (normalizedArtist.isNotBlank() && (artistStr.contains(normalizedArtist) || normalizedArtist.contains(artistStr))) score += 40
            if (normalizedAlbum.isNotBlank() && album == normalizedAlbum) score += 20
            
            if (durationDiff < 3) score += 30
            else if (durationDiff < 10) score += 10

            if (score > bestScore && score > 0) {
                bestScore = score
                bestMid = song.optString("mid")
            }
        }

        return bestMid?.let { QQMusicSearchResult(it, bestScore) }
    }

    private data class QQMusicSearchResult(val mid: String, val score: Int)

    private fun fetchLyrics(songMid: String): JSONObject? {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&format=json&g_tk=5381"
        val response = requestGet(url)
        if (response.code !in 200..299) return null
        
        return JSONObject(response.body)
    }

    private fun mergeTranslation(base: List<LyricsLine>, translation: List<LyricsLine>): List<LyricsLine> {
        if (translation.isEmpty()) return base
        return base.mapIndexed { index, line ->
            val nextStart = base.getOrNull(index + 1)?.startTimeMs ?: Long.MAX_VALUE
            val matched = translation
                .filter { it.startTimeMs <= line.startTimeMs && it.startTimeMs < nextStart }
                .minByOrNull { line.startTimeMs - it.startTimeMs }

            if (matched != null && line.startTimeMs - matched.startTimeMs <= 8000) {
                line.copy(translation = matched.text)
            } else {
                line
            }
        }
    }

    private fun requestGet(url: String): HttpResponse {
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return HttpClient.okHttpClient.newCall(request).execute().use { response ->
            HttpResponse(
                code = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }

    private fun requestPost(url: String, jsonPayload: String): HttpResponse {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = jsonPayload.toRequestBody(mediaType)

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return HttpClient.okHttpClient.newCall(request).execute().use { response ->
            HttpResponse(
                code = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private data class HttpResponse(val code: Int, val body: String)
}
