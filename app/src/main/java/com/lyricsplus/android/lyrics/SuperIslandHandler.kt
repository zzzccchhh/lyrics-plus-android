package com.lyricsplus.android.lyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import com.lyricsplus.android.MainActivity
import com.lyricsplus.android.shizuku.XmsfNetworkHelper
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.island.model.TextInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SuperIslandHandler(
    private val context: Context,
    private val foregroundStarter: (Notification, String) -> Unit
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val networkMutex = Mutex()

    private var running = false
    private var lastSignature = ""
    private var lastNotifyElapsed = 0L
    private var firstNotification = true
    private var xmsfCutActive = false
    private var networkJob: Job? = null

    init {
        createChannel()
    }

    fun start() {
        if (running) return
        running = true
        firstNotification = true
        xmsfCutActive = false
        lastSignature = ""
        lastNotifyElapsed = 0L
    }

    fun stop() {
        if (!running) return
        running = false
        networkJob?.cancel()
        manager.cancel(NOTIFICATION_ID)
        scope.launch(Dispatchers.IO) {
            XmsfNetworkHelper.setXmsfNetworkingEnabled(context, true)
        }
    }

    fun render(state: SuperIslandState) {
        if (!running) return

        val signature = listOf(
            state.title,
            state.artist,
            state.leftLyric ?: state.lyric,
            state.rightLyric ?: state.fullLyric,
            state.isPlaying
        ).joinToString("|")
        val now = android.os.SystemClock.elapsedRealtime()
        val sameSignature = signature == lastSignature
        if (!firstNotification && sameSignature && now - lastNotifyElapsed < FORCE_REFRESH_INTERVAL_MS) {
            Log.d(TAG, "skip same signature elapsed=${now - lastNotifyElapsed}ms")
            return
        }
        if (!firstNotification && now - lastNotifyElapsed < MIN_NOTIFY_INTERVAL_MS) {
            Log.d(TAG, "skip throttle elapsed=${now - lastNotifyElapsed}ms")
            return
        }

        val notification = buildNotification(state)

        lastSignature = signature
        lastNotifyElapsed = now

        notifyWithOptionalXmsfBypass(notification)
        firstNotification = false
    }

    private fun buildNotification(state: SuperIslandState): Notification {
        val displayLyric = state.fullLyric.ifBlank { state.lyric.ifBlank { state.title.ifBlank { "♪" } } }
        val islandTriggerText = displayLyric.cleanIslandLyricInput().ifBlank { "♪" }
        val notificationTitle = FIXED_MEASURE_TEXT
        val notificationText = FIXED_CONTENT_TEXT
        val color = readableIslandColor(state.accentColor)
        val appIcon = renderAppIcon()

        val extras = FocusNotification.buildV3 {
            business = "lyric_display"
            isShowNotification = true
            enableFloat = false
            updatable = true
            islandFirstFloat = false
            aodTitle = notificationTitle

            ticker = notificationTitle
            val appIconKey = createPicture("miui.focus.pic_app", appIcon)
            tickerPic = appIconKey

            chatInfo {
                picProfile = appIconKey
                title = notificationTitle
                content = notificationText
                appIconPkg = state.mediaPackage.ifBlank { context.packageName }
            }

            island {
                islandProperty = 1
                highlightColor = color

                bigIslandArea {
                    val split = if (!state.leftLyric.isNullOrBlank() || !state.rightLyric.isNullOrBlank()) {
                        normalizeProvidedSplit(
                            left = state.leftLyric.orEmpty(),
                            right = state.rightLyric.orEmpty(),
                        )
                    } else {
                        splitFullLyric(displayLyric)
                    }
                    Log.d(
                        TAG,
                        "payload left='${split.left.logSafe()}' right='${split.right.logSafe()}' " +
                            "leftLen=${split.left.length} rightLen=${split.right.length} " +
                            "leftWeight=${split.left.lyricWeight()} rightWeight=${split.right.lyricWeight()} " +
                            "leftPlaceholders=${split.left.placeholderCount()} rightPlaceholders=${split.right.placeholderCount()} " +
                            "narrow=${split.narrowFont}"
                    )
                    imageTextInfoLeft {
                        type = 1
                        textInfo {
                            title = split.left.ifBlank { "♪" }
                            showHighlightColor = true
                            narrowFont = split.narrowFont
                            turnAnim = false
                        }
                    }
                    textInfo = TextInfo().apply {
                        title = split.right.ifBlank { "♪" }
                        showHighlightColor = true
                        narrowFont = split.narrowFont
                        turnAnim = false
                    }
                }

                smallIslandArea {
                    combinePicInfo {
                        progressInfo {
                            progress = state.progressPercent.coerceIn(0, 100)
                            colorReach = color
                            colorUnReach = SMALL_PROGRESS_UNREACH_COLOR
                        }
                    }
                }

            }
        }
        extras.putString("miui.focus.lyric_trigger", islandTriggerText)

        return baseBuilder()
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setTicker(null)
            .setSubText(null)
            .setColor(state.accentColor)
            .addExtras(extras)
            .build()
    }

    private fun notifyWithOptionalXmsfBypass(notification: Notification) {
        val prefs = context.getSharedPreferences("lyrics_plus_prefs", Context.MODE_PRIVATE)
        val useBypass = prefs.getBoolean("super_island_xmsf_bypass", true)

        if (!useBypass) {
            dispatch(notification, "direct")
            return
        }

        if (xmsfCutActive) {
            dispatch(notification, "direct")
            return
        }

        networkJob?.cancel()
        networkJob = scope.launch(Dispatchers.IO) {
            networkMutex.withLock {
                val cut = withContext(NonCancellable) {
                    XmsfNetworkHelper.setXmsfNetworkingEnabled(context, false)
                }
                withContext(Dispatchers.Main) {
                    dispatch(notification, if (cut) "shizuku-cut" else "shizuku-fallback")
                    if (cut) {
                        xmsfCutActive = true
                    }
                }
            }
        }
    }

    private fun dispatch(notification: Notification, reason: String) {
        foregroundStarter(notification, reason)
    }

    private fun baseBuilder(): Notification.Builder {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    it.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "小米超级岛歌词",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "用于在小米超级岛显示实时歌词"
            setSound(null, null)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private data class LyricSplit(val left: String, val right: String, val narrowFont: Boolean)

    private fun normalizeProvidedSplit(left: String, right: String): LyricSplit {
        val cleanLeft = left.withoutIslandPlaceholders()
        val cleanRight = right.withoutIslandPlaceholders()
        if (cleanLeft.isLatinIslandText() && cleanRight.isLatinIslandText()) {
            val fixedLeft = cleanLeft.take(TIMED_LATIN_MAX_CHARS).trimEnd()
            val fixedRight = cleanRight.take(TIMED_LATIN_MAX_CHARS).trimEnd()
            val filler = listOf(fixedLeft, fixedRight)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return LyricSplit(
                left = fixedLeft.fillStartWithLatinLyrics(TIMED_LATIN_MAX_CHARS, filler),
                right = fixedRight.fillEndWithLatinLyrics(TIMED_LATIN_MAX_CHARS, filler),
                narrowFont = false
            )
        }
        val fixedLeft = left.cleanIslandLyricInput().takeByWeightFromEnd(TIMED_SIDE_WEIGHT)
        val fixedRight = right.cleanIslandLyricInput().takeByWeight(TIMED_SIDE_WEIGHT)
        val filler = listOf(fixedLeft, fixedRight)
            .filter { it.isNotBlank() }
            .joinToString("")
        return LyricSplit(
            left = fixedLeft.fillStartWithLyricsByWeight(TIMED_SIDE_WEIGHT, filler),
            right = fixedRight.fillEndWithLyricsByWeight(TIMED_SIDE_WEIGHT, filler),
            narrowFont = false
        )
    }

    private fun splitFullLyric(text: String): LyricSplit {
        val normalized = text.trim()
        if (normalized.isEmpty()) return LyricSplit("", "", false)
        if (normalized.isLatinIslandText()) {
            return splitLatinFullLyric(normalized)
        }

        val narrowFont = false
        val sideCapacity = NORMAL_SIDE_WEIGHT
        val visible = normalized.takeByWeight(sideCapacity * 2)
        val splitIndex = if (visible.shouldHardSplitForStability()) {
            findHardSplitIndex(visible, sideCapacity)
        } else {
            findMostSymmetricSplitIndex(visible, sideCapacity)
        }
        val left = visible.substring(0, splitIndex).trim().takeByWeight(sideCapacity)
        val right = visible.substring(splitIndex).trim().takeByWeight(sideCapacity)
        val filler = listOf(left, right)
            .filter { it.isNotBlank() }
            .joinToString("")
        return LyricSplit(
            left = left.fillStartWithLyricsByWeight(sideCapacity, filler),
            right = right.fillEndWithLyricsByWeight(sideCapacity, filler),
            narrowFont = narrowFont
        )
    }

    private fun findMostSymmetricSplitIndex(text: String, sideCapacity: Int): Int {
        if (text.length <= 1) return text.length
        var bestIndex = 1
        var bestScore = Int.MAX_VALUE

        val wordBoundaryCandidates = (1 until text.length).filter { index ->
            text[index - 1].isWhitespace() || text[index].isWhitespace()
        }
        val candidates = wordBoundaryCandidates.ifEmpty { (1 until text.length).toList() }

        for (index in candidates) {
            val leftWeight = text.substring(0, index).trim().lyricWeight()
            val rightWeight = text.substring(index).trim().lyricWeight()
            if (leftWeight > sideCapacity || rightWeight > sideCapacity) continue
            val leftBiasPenalty = if (leftWeight < rightWeight) 3 else 0
            val score = kotlin.math.abs(leftWeight - rightWeight) * 10 + leftBiasPenalty
            if (score < bestScore) {
                bestScore = score
                bestIndex = index
            }
        }

        if (bestScore == Int.MAX_VALUE && candidates !== wordBoundaryCandidates) {
            return 1
        }
        if (bestScore == Int.MAX_VALUE) {
            return findMostSymmetricSplitIndex(text.replace(Regex("\\s+"), ""), sideCapacity)
        }
        return bestIndex
    }

    private fun findHardSplitIndex(text: String, sideCapacity: Int): Int {
        var weight = 0
        for (index in text.indices) {
            weight += text[index].lyricCharWeight()
            if (weight > sideCapacity) return index.coerceAtLeast(1)
        }
        return text.length
    }

    private fun String.takeByWeight(maxWeight: Int): String {
        var weight = 0
        var end = 0
        for (index in indices) {
            weight += this[index].lyricCharWeight()
            if (weight > maxWeight) break
            end = index + 1
        }
        return substring(0, end).trim()
    }

    private fun String.takeByWeightFromEnd(maxWeight: Int): String {
        var weight = 0
        var start = length
        for (index in indices.reversed()) {
            weight += this[index].lyricCharWeight()
            if (weight > maxWeight) break
            start = index
        }
        return substring(start).trim()
    }

    private fun String.lyricWeight(): Int = sumOf { it.lyricCharWeight() }

    private fun String.placeholderCount(): Int =
        count { it == FULL_WIDTH_PLACEHOLDER.first() || it == HALF_WIDTH_PLACEHOLDER.first() }

    private fun String.logSafe(): String =
        replace(FULL_WIDTH_PLACEHOLDER, "[FW]")
            .replace(HALF_WIDTH_PLACEHOLDER, "[HW]")

    private fun String.shouldHardSplitForStability(): Boolean =
        any { it.isLetterOrDigit() } && any { it.isWhitespace() } && none { it.isCjkLikeForIsland() }

    private fun String.isLatinIslandText(): Boolean {
        val visible = normalizeIslandLatinInput()
        if (visible.isEmpty()) return true
        return visible.any { it.isLetterOrDigit() } && visible.none { it.isCjkLikeForIsland() }
    }

    private fun String.withoutIslandPlaceholders(): String =
        normalizeIslandLatinInput()

    private fun String.cleanIslandLyricInput(): String =
        filterNot {
            it == FULL_WIDTH_PLACEHOLDER.first() ||
                it == HALF_WIDTH_PLACEHOLDER.first() ||
                Character.getType(it) == Character.FORMAT.toInt()
        }
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.latinWords(): List<String> =
        Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)?[!?.,:;]*").findAll(normalizeIslandLatinInput())
            .map { it.value }
            .toList()

    private fun String.normalizeIslandLatinInput(): String =
        filterNot {
            it == FULL_WIDTH_PLACEHOLDER.first() ||
                it == HALF_WIDTH_PLACEHOLDER.first() ||
                Character.getType(it) == Character.FORMAT.toInt()
        }
            .map { if (it.isAnySpace()) ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun Char.isAnySpace(): Boolean =
        isWhitespace() || Character.isSpaceChar(this)

    private fun splitLatinFullLyric(text: String): LyricSplit {
        val normalized = text.normalizeIslandLatinInput()
        if (normalized.isEmpty()) return LyricSplit("", "", false)

        val left = normalized.take(TIMED_LATIN_MAX_CHARS).trimEnd()
        val right = normalized.drop(TIMED_LATIN_MAX_CHARS).take(TIMED_LATIN_MAX_CHARS).trimEnd()
        val filler = listOf(left, right)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return LyricSplit(
            left = left.fillStartWithLatinLyrics(TIMED_LATIN_MAX_CHARS, filler),
            right = right.fillEndWithLatinLyrics(TIMED_LATIN_MAX_CHARS, filler),
            narrowFont = false
        )
    }

    private fun String.fillStartWithLatinLyrics(targetChars: Int, filler: String): String {
        if (length >= targetChars) return takeLast(targetChars)
        val missing = targetChars - length
        val prefix = repeatedLatinFiller(filler, missing).takeLast(missing)
        return prefix + this
    }

    private fun String.fillEndWithLatinLyrics(targetChars: Int, filler: String): String {
        if (length >= targetChars) return take(targetChars)
        val missing = targetChars - length
        val suffix = repeatedLatinFiller(filler, missing).take(missing)
        return this + suffix
    }

    private fun repeatedLatinFiller(filler: String, minChars: Int): String {
        val clean = filler.normalizeIslandLatinInput()
        if (clean.isEmpty() || minChars <= 0) return ""
        val builder = StringBuilder()
        while (builder.length < minChars) {
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(clean)
        }
        return builder.toString()
    }

    private fun String.fillStartWithLyricsByWeight(targetWeight: Int, filler: String): String {
        val text = takeByWeightFromEnd(targetWeight)
        val missing = (targetWeight - text.lyricWeight()).coerceAtLeast(0)
        if (missing == 0) return text
        val prefix = repeatedLyricFiller(filler, missing).takeByWeightFromEnd(missing)
        return (prefix + text).takeByWeightFromEnd(targetWeight)
    }

    private fun String.fillEndWithLyricsByWeight(targetWeight: Int, filler: String): String {
        val text = takeByWeight(targetWeight)
        val missing = (targetWeight - text.lyricWeight()).coerceAtLeast(0)
        if (missing == 0) return text
        val suffix = repeatedLyricFiller(filler, missing).takeByWeight(missing)
        return (text + suffix).takeByWeight(targetWeight)
    }

    private fun repeatedLyricFiller(filler: String, minWeight: Int): String {
        val clean = filler.cleanIslandLyricInput()
        if (clean.isEmpty() || minWeight <= 0) return ""
        val builder = StringBuilder()
        while (builder.toString().lyricWeight() < minWeight) {
            builder.append(clean)
        }
        return builder.toString()
    }

    private fun Char.lyricCharWeight(): Int {
        if (this == FULL_WIDTH_PLACEHOLDER.first()) return 2
        if (this == HALF_WIDTH_PLACEHOLDER.first()) return 1
        if (isWhitespace()) return 1
        return when (Character.UnicodeBlock.of(this)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES -> 2
            else -> 1
        }
    }

    private fun Char.isCjkLikeForIsland(): Boolean {
        return when (Character.UnicodeBlock.of(this)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES -> true
            else -> false
        }
    }

    private fun renderAppIcon(): Icon {
        val drawable = context.applicationInfo.loadIcon(context.packageManager)
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return Icon.createWithBitmap(bitmap)
    }

    private fun readableIslandColor(accentColor: Int): String {
        val red = android.graphics.Color.red(accentColor)
        val green = android.graphics.Color.green(accentColor)
        val blue = android.graphics.Color.blue(accentColor)
        val luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255.0
        val saturation = run {
            val max = maxOf(red, green, blue)
            val min = minOf(red, green, blue)
            if (max == 0) 0.0 else (max - min).toDouble() / max
        }
        val readable = if (luminance < 0.62 || saturation < 0.28) {
            0xFFFFFFFF.toInt()
        } else {
            accentColor
        }
        return String.format("#FF%06X", readable and 0xFFFFFF)
    }

    companion object {
        const val CHANNEL_ID = "super_island_lyrics_channel"
        const val NOTIFICATION_ID = 1003
        private const val TAG = "SuperIslandLyrics"
        private const val MIN_NOTIFY_INTERVAL_MS = 180L
        private const val FORCE_REFRESH_INTERVAL_MS = 2400L
        private const val NORMAL_SIDE_WEIGHT = 16
        private const val TIMED_SIDE_WEIGHT = 16
        private const val TIMED_LATIN_MAX_CHARS = 20
        private const val FIXED_MEASURE_TEXT = "Lyrics Plus"
        private const val FIXED_CONTENT_TEXT = "Super Island Lyrics"
        private const val SMALL_PROGRESS_UNREACH_COLOR = "#333333"
        private const val FULL_WIDTH_PLACEHOLDER = "\u3164"
        private const val HALF_WIDTH_PLACEHOLDER = "\u2800"
    }

}
