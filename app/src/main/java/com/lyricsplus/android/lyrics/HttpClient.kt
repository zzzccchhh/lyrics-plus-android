package com.lyricsplus.android.lyrics

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object HttpClient {
    private var cacheDir: File? = null

    fun initialize(context: Context) {
        if (cacheDir == null) {
            cacheDir = File(context.applicationContext.cacheDir, "http_cache")
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)

        cacheDir?.let { dir ->
            runCatching {
                val cacheSize = 10 * 1024 * 1024L // 10 MB Cache
                builder.cache(Cache(dir, cacheSize))
            }
        }

        builder.build()
    }
}
