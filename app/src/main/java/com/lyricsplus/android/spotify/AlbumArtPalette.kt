package com.lyricsplus.android.spotify

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

data class AlbumArtPalette(
    val start: String,
    val end: String
)

object AlbumArtPaletteExtractor {
    fun fromBitmap(bitmap: Bitmap?): AlbumArtPalette? {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null

        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        val stepX = (bitmap.width / 32).coerceAtLeast(1)
        val stepY = (bitmap.height / 32).coerceAtLeast(1)

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) > 24) {
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                    count += 1
                }
                x += stepX
            }
            y += stepY
        }

        if (count == 0L) return null

        val base = Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
        return AlbumArtPalette(
            start = base.toAdjustedHex(saturation = 1.28f, value = 1.08f),
            end = base.toAdjustedHex(saturation = 1.08f, value = 0.34f)
        )
    }

    private fun Int.toAdjustedHex(saturation: Float, value: Float): String {
        val hsv = FloatArray(3)
        Color.colorToHSV(this, hsv)
        hsv[1] = (hsv[1] * saturation).coerceIn(0.32f, 0.92f)
        hsv[2] = (hsv[2] * value).coerceIn(0.16f, 0.86f)
        return Color.HSVToColor(hsv).toHex()
    }

    private fun Int.toHex(): String =
        "#%02X%02X%02X".format(Color.red(this), Color.green(this), Color.blue(this))
}
