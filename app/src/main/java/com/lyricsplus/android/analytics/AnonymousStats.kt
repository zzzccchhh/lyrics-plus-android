package com.lyricsplus.android.analytics

import android.content.Context
import android.os.Build
import com.lyricsplus.android.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object AnonymousStats {
    private const val PREFS_NAME = "lyrics_plus_stats"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_ENABLED = "enabled"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val sessionId = UUID.randomUUID().toString()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    fun isAvailable(): Boolean = BuildConfig.STATS_ENDPOINT.isNotBlank()

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun trackAppOpen(context: Context) {
        track(context, "app_open")
    }

    fun trackFeatureToggle(context: Context, feature: String, enabled: Boolean) {
        track(
            context = context,
            event = "feature_toggle",
            properties = mapOf(
                "feature" to feature,
                "enabled" to enabled.toString()
            )
        )
    }

    fun trackReadingModeChanged(context: Context, mode: Int) {
        track(
            context = context,
            event = "reading_mode_changed",
            properties = mapOf("readingMode" to mode.toString())
        )
    }

    fun trackLyricsFetchResult(context: Context, source: String, success: Boolean) {
        track(
            context = context,
            event = "lyrics_fetch_result",
            properties = mapOf(
                "source" to source,
                "result" to if (success) "success" else "failure"
            )
        )
    }

    private fun track(
        context: Context,
        event: String,
        properties: Map<String, String> = emptyMap()
    ) {
        val appContext = context.applicationContext
        val endpoint = BuildConfig.STATS_ENDPOINT
        if (endpoint.isBlank() || !isEnabled(appContext)) return

        val payload = JSONObject()
            .put("installId", installId(appContext))
            .put("sessionId", sessionId)
            .put("event", event)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("androidSdk", Build.VERSION.SDK_INT)
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("timestamp", System.currentTimeMillis())
            .put("properties", JSONObject(properties))

        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", "lyrics-plus-android/${BuildConfig.VERSION_NAME}")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun installId(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_INSTALL_ID, null)?.let { return it }

        return synchronized(this) {
            prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also { id ->
                prefs.edit().putString(KEY_INSTALL_ID, id).apply()
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
