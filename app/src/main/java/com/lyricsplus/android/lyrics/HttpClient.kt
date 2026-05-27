package com.lyricsplus.android.lyrics

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClient {
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
