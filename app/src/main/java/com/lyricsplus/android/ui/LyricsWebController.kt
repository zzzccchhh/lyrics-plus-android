package com.lyricsplus.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lyricsplus.android.data.LyricsLine
import com.lyricsplus.android.data.NowPlaying
import org.json.JSONArray
import org.json.JSONObject

const val WebLogTag = "LyricsPlusWeb"

@SuppressLint("SetJavaScriptEnabled")
class LyricsWebController(context: Context) {
    var isReady by mutableStateOf(false)
        private set

    var debugMessage by mutableStateOf("WebView starting")
        private set

    var isFullLyricsMode by mutableStateOf(false)
        private set

    val webView: WebView = WebView(context).apply {
        WebView.setWebContentsDebuggingEnabled(true)
        Log.d(WebLogTag, "create native WebView")
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = true
        settings.allowContentAccess = false
        addJavascriptInterface(LyricsWebBridge(::setDebug, ::setFullMode), "AndroidLyrics")
        overScrollMode = WebView.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    WebLogTag,
                    "console ${consoleMessage.messageLevel()} ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
                )
                setDebug("console: ${consoleMessage.message()}")
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                Log.d(WebLogTag, "pageFinished url=$url")
                isReady = true
                setDebug("WebView page finished")
                pushStatusBarHeight()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                val description = "webError ${error.errorCode} ${error.description} url=${request.url}"
                Log.e(WebLogTag, description)
                setDebug(description)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val description = "renderProcessGone didCrash=${detail.didCrash()}"
                Log.e(WebLogTag, description)
                setDebug(description)
                return true
            }
        }

        Log.d(WebLogTag, "load assets html")
        loadUrl("file:///android_asset/lyrics-web/index.html")
    }

    fun pushTrackAndLyrics(track: NowPlaying, lyrics: List<LyricsLine>, positionMs: Long, isPlaying: Boolean) {
        if (!isReady) return
        val trackJson = track.toJson().toString()
        val lyricsJson = lyrics.toJson().toString()
        Log.d(WebLogTag, "push track=${track.track} artist=${track.artist} lyrics=${lyrics.size} jsonBytes=${lyricsJson.length}")
        webView.evaluateJavascript("window.LyricsPlus.setTrack($trackJson)", ::logJsResult)
        webView.evaluateJavascript("window.LyricsPlus.setLyrics($lyricsJson)", ::logJsResult)
        pushPlayback(positionMs, isPlaying)
    }

    fun pushPlayback(positionMs: Long, isPlaying: Boolean) {
        if (!isReady) return
        webView.evaluateJavascript("window.LyricsPlus.setPlaybackState($positionMs, $isPlaying)", null)
    }

    fun pushRomajiState(show: Boolean) {
        if (!isReady) return
        webView.evaluateJavascript("window.LyricsPlus.setShowRomaji($show)", null)
    }

    fun pushAnimationDuration(seconds: Int) {
        if (!isReady) return
        webView.evaluateJavascript("window.LyricsPlus.setAnimationDuration($seconds)", null)
    }

    fun pushAnimationPlayState(running: Boolean) {
        if (!isReady) return
        webView.evaluateJavascript("window.LyricsPlus.setAnimationPlayState($running)", null)
    }

    private fun pushStatusBarHeight() {
        val resourceId = webView.context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val heightPx = if (resourceId > 0) webView.context.resources.getDimensionPixelSize(resourceId) else 0
        val density = webView.context.resources.displayMetrics.density
        val heightDp = if (density > 0) (heightPx / density).toInt() else 28
        Log.d(WebLogTag, "statusBarHeight=${heightDp}dp")
        webView.evaluateJavascript(
            "document.getElementById('stage').style.setProperty('--status-bar-height', '${heightDp}px')",
            null
        )
    }

    fun pushHeaderHeight(heightPx: Int) {
        if (!isReady) return
        val density = webView.context.resources.displayMetrics.density
        val heightDp = if (density > 0) (heightPx / density).toInt() else 0
        Log.d(WebLogTag, "headerBottom=${heightDp}dp")
        webView.evaluateJavascript(
            "document.getElementById('stage').style.setProperty('--header-bottom', '${heightDp}px')",
            null
        )
    }

    fun destroy() {
        webView.destroy()
    }

    private fun setDebug(message: String) {
        debugMessage = message
    }

    private fun setFullMode(full: Boolean) {
        isFullLyricsMode = full
    }
}

private class LyricsWebBridge(
    private val onDebug: (String) -> Unit,
    private val onFullLyricsMode: (Boolean) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun report(message: String) {
        Log.d(WebLogTag, "bridge $message")
        mainHandler.post {
            onDebug(message)
        }
    }

    @JavascriptInterface
    fun setFullLyricsMode(full: Boolean) {
        Log.d(WebLogTag, "bridge fullLyricsMode=$full")
        mainHandler.post {
            onFullLyricsMode(full)
        }
    }
}

private fun NowPlaying.toJson(): JSONObject =
    JSONObject()
        .put("track", track)
        .put("artist", artist)
        .put("album", album)
        .put("durationSeconds", durationSeconds)
        .put("backgroundStart", backgroundStart)
        .put("backgroundEnd", backgroundEnd)

private fun List<LyricsLine>.toJson(): JSONArray =
    JSONArray().also { array ->
        forEach { line ->
            array.put(
                JSONObject()
                    .put("startTimeMs", line.startTimeMs)
                    .put("text", line.text)
                    .put("translation", line.translation)
                    .put("reading", line.reading)
            )
        }
    }

private fun logJsResult(value: String?) {
    if (value != null && value != "null") {
        Log.d(WebLogTag, "evaluate result=$value")
    }
}
