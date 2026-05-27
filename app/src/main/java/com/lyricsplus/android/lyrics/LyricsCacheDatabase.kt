package com.lyricsplus.android.lyrics

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lyricsplus.android.data.LyricsLine
import org.json.JSONArray
import org.json.JSONObject

class LyricsCacheDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_LYRICS (
                $COLUMN_KEY TEXT PRIMARY KEY,
                $COLUMN_CONTENT TEXT,
                $COLUMN_CACHED_AT INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LYRICS")
        onCreate(db)
    }

    fun getLyrics(trackKey: String): List<LyricsLine>? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_LYRICS,
            arrayOf(COLUMN_CONTENT),
            "$COLUMN_KEY = ?",
            arrayOf(trackKey),
            null, null, null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                val jsonStr = it.getString(0)
                runCatching { parseJson(jsonStr) }.getOrNull()
            } else {
                null
            }
        }
    }

    fun saveLyrics(trackKey: String, lyrics: List<LyricsLine>) {
        val db = writableDatabase
        val jsonStr = toJson(lyrics)
        
        val values = ContentValues().apply {
            put(COLUMN_KEY, trackKey)
            put(COLUMN_CONTENT, jsonStr)
            put(COLUMN_CACHED_AT, System.currentTimeMillis())
        }

        db.insertWithOnConflict(
            TABLE_LYRICS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun parseJson(jsonStr: String): List<LyricsLine> {
        val array = JSONArray(jsonStr)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            LyricsLine(
                startTimeMs = obj.getLong("startTimeMs"),
                text = obj.getString("text"),
                translation = obj.optString("translation", ""),
                reading = obj.optString("reading", "")
            )
        }
    }

    private fun toJson(lyrics: List<LyricsLine>): String {
        val array = JSONArray()
        lyrics.forEach { line ->
            array.put(
                JSONObject().apply {
                    put("startTimeMs", line.startTimeMs)
                    put("text", line.text)
                    put("translation", line.translation)
                    put("reading", line.reading)
                }
            )
        }
        return array.toString()
    }

    companion object {
        private const val DATABASE_NAME = "lyrics_cache.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_LYRICS = "cached_lyrics"
        private const val COLUMN_KEY = "track_key"
        private const val COLUMN_CONTENT = "content"
        private const val COLUMN_CACHED_AT = "cached_at"
    }
}
