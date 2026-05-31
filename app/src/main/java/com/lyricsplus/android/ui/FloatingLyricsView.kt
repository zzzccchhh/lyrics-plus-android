package com.lyricsplus.android.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricsplus.android.data.LyricsLine
import com.lyricsplus.android.lyrics.FloatingLyricsService

data class Syllable(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

@Composable
fun FloatingLyricsView(
    service: FloatingLyricsService,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val track = service.currentTrack
    val lyrics = service.currentLyrics
    val estimatedTime = service.estimatedPositionMs + service.lyricsOffsetMs
    
    val activeIndex = findActiveIndex(estimatedTime, lyrics)
    val activeLine = lyrics.getOrNull(activeIndex)

    val backgroundOpacity = service.backgroundOpacity
    val textSizeSp = service.textSizeSp
    val isLocked = service.isLocked
    val textColor = runCatching { Color(android.graphics.Color.parseColor(service.textColorHex)) }.getOrDefault(Color.White)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main floating bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE6101211).copy(alpha = backgroundOpacity))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle (Left ⋮⋮)
            if (!isLocked) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⋮⋮",
                        color = Color(0x66FFFFFF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // When locked, show a tiny unlock ghost button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0x26FFFFFF), CircleShape)
                        .clickable {
                            service.isLocked = false
                            service.updateWindowTouchability(touchable = true)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔒",
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Lyric active line presentation (Hardware accelerated sweep)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (track == null) {
                    Text(
                        text = "等待 Spotify 播放音乐...",
                        color = Color(0xB3FFFFFF),
                        fontSize = textSizeSp.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (activeLine == null) {
                    Text(
                        text = "纯音乐 / 搜索歌词中...",
                        color = Color(0xB3FFFFFF),
                        fontSize = textSizeSp.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    val syllables = remember(activeLine.text, activeLine.startTimeMs) {
                        parseSyllables(activeLine.text, activeLine.startTimeMs)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        syllables.forEach { syllable ->
                            SyllableTextSweep(
                                syllable = syllable,
                                currentTimeMs = estimatedTime,
                                textSizeSp = textSizeSp,
                                textColor = textColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Control buttons (⚙, 🔒/🔓, ✕)
            if (!isLocked) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Lock overlay button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0x1AFFFFFF), CircleShape)
                            .clickable {
                                service.isLocked = true
                                service.updateWindowTouchability(touchable = false)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔓", fontSize = 12.sp)
                    }

                    // Toggle expand settings button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(if (isExpanded) Color(0xFF4AD295) else Color(0x1AFFFFFF), CircleShape)
                            .clickable { isExpanded = !isExpanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isExpanded) "✕" else "⚙",
                            color = if (isExpanded) Color.Black else Color.White,
                            fontSize = 12.sp
                        )
                    }

                    // Close overlay button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0x33FF5555), CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Expanded Settings Panel
        AnimatedVisibility(
            visible = isExpanded && !isLocked,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xF2161A18))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Text Size adjust
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "字号大小", color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                .clickable {
                                    if (service.textSizeSp > 12f) {
                                        service.textSizeSp -= 1f
                                        service.savePrefs()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(text = "${textSizeSp.toInt()}sp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                .clickable {
                                    if (service.textSizeSp < 26f) {
                                        service.textSizeSp += 1f
                                        service.savePrefs()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Row 2: Background Opacity adjust
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "背景透明度", color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                .clickable {
                                    if (service.backgroundOpacity > 0.15f) {
                                        service.backgroundOpacity -= 0.1f
                                        service.savePrefs()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(text = "${(backgroundOpacity * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                .clickable {
                                    if (service.backgroundOpacity < 0.95f) {
                                        service.backgroundOpacity += 0.1f
                                        service.savePrefs()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Row 3: Colors list selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "字体颜色", color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val colorsMap = mapOf(
                            "#FFFFFF" to Color.White,
                            "#4AD295" to Color(0xFF4AD295),
                            "#F06AA5" to Color(0xFFF06AA5),
                            "#3FA9F5" to Color(0xFF3FA9F5)
                        )
                        colorsMap.forEach { (hex, color) ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        width = if (service.textColorHex == hex) 2.dp else 0.dp,
                                        color = if (service.textColorHex == hex) Color.Black else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        service.textColorHex = hex
                                        service.savePrefs()
                                    }
                            )
                        }
                    }
                }

                // Row 4: Lyrics Offset adjustment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val offsetSec = service.lyricsOffsetMs / 1000.0
                    val offsetText = if (service.lyricsOffsetMs > 0) "+${offsetSec}s" else if (service.lyricsOffsetMs < 0) "${offsetSec}s" else "0.0s"
                    Text(text = "歌词微调 [$offsetText]", color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                .clickable { service.lyricsOffsetMs -= 500 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = " 🐰 -0.5s ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                .clickable { service.lyricsOffsetMs = 0L },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = " 🔄 重置 ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                .clickable { service.lyricsOffsetMs += 500 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = " 🐢 +0.5s ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyllableTextSweep(
    syllable: Syllable,
    currentTimeMs: Long,
    textSizeSp: Float,
    textColor: Color
) {
    val start = syllable.startMs
    val end = syllable.endMs
    
    val sweepProgress = when {
        currentTimeMs >= end -> 1f
        currentTimeMs < start -> 0f
        else -> {
            val duration = end - start
            if (duration > 0) ((currentTimeMs - start).toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 1f
        }
    }

    // Hardware accelerated linear gradient brush with hard stops
    val brush = if (sweepProgress <= 0f) {
        Brush.linearGradient(
            colors = listOf(textColor.copy(alpha = 0.35f), textColor.copy(alpha = 0.35f))
        )
    } else if (sweepProgress >= 1f) {
        Brush.linearGradient(
            colors = listOf(textColor, textColor)
        )
    } else {
        Brush.linearGradient(
            0f to textColor,
            sweepProgress to textColor,
            sweepProgress to textColor.copy(alpha = 0.35f),
            1f to textColor.copy(alpha = 0.35f)
        )
    }

    Text(
        text = syllable.text,
        fontSize = textSizeSp.sp,
        fontWeight = FontWeight.Black,
        style = TextStyle(brush = brush),
        maxLines = 1,
        overflow = TextOverflow.Clip
    )
}

fun findActiveIndex(positionMs: Long, lyrics: List<LyricsLine>): Int {
    if (lyrics.isEmpty()) return -1
    if (positionMs < lyrics[0].startTimeMs) return 0
    var low = 0
    var high = lyrics.size - 1
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        val midTime = lyrics[mid].startTimeMs
        if (positionMs >= midTime) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

fun parseSyllables(lineText: String, lineStartTime: Long): List<Syllable> {
    val syllables = mutableListOf<Syllable>()
    if (lineText.isBlank()) return syllables

    // 1. Enhanced LRC: <00:12.34>word
    val lrcRegex = Regex("<(\\d{2}):(\\d{2})[.:](\\d{2,3})>([^<]*)")
    // 2. NetEase YRC prefix: (12580,250)word
    val prefixRegex = Regex("\\((\\d+),(\\d+)(?:,\\d+)?\\)([^\\(<]+)")
    // 3. QQ Music YRC suffix: word(293,293)
    val suffixRegex = Regex("([^\\(<\\)]+)\\s*\\((\\d+),(\\d+)\\)")

    val lrcMatches = lrcRegex.findAll(lineText).toList()
    if (lrcMatches.isNotEmpty()) {
        for (match in lrcMatches) {
            val min = match.groupValues[1].toLong()
            val sec = match.groupValues[2].toLong()
            val fracStr = match.groupValues[3]
            val text = match.groupValues[4]
            var frac = fracStr.toLong()
            if (fracStr.length == 2) frac *= 10
            val timeMs = min * 60000 + sec * 1000 + frac
            syllables.add(Syllable(text, timeMs, timeMs + 300))
        }
    } else {
        val isPrefix = lineText.trim().startsWith("(")
        if (isPrefix) {
            val matches = prefixRegex.findAll(lineText).toList()
            for (match in matches) {
                val startMs = match.groupValues[1].toLong()
                val durationMs = match.groupValues[2].toLong()
                val text = match.groupValues[3]
                
                val isRelative = (startMs < lineStartTime) && (startMs < 1000 || (lineStartTime - startMs) > (lineStartTime * 0.5).coerceAtLeast(2000.0))
                val timeMs = if (isRelative) (lineStartTime + startMs) else startMs
                syllables.add(Syllable(text, timeMs, timeMs + durationMs))
            }
        } else {
            val matches = suffixRegex.findAll(lineText).toList()
            for (match in matches) {
                val text = match.groupValues[1]
                val startMs = match.groupValues[2].toLong()
                val durationMs = match.groupValues[3].toLong()

                val isRelative = (startMs < lineStartTime) && (startMs < 1000 || (lineStartTime - startMs) > (lineStartTime * 0.5).coerceAtLeast(2000.0))
                val timeMs = if (isRelative) (lineStartTime + startMs) else startMs
                syllables.add(Syllable(text, timeMs, timeMs + durationMs))
            }
        }
    }

    if (syllables.isEmpty()) {
        val cleanText = lineText
            .replace(Regex("<\\d{2}:\\d{2}[.:]\\d{1,3}>"), "")
            .replace(Regex("\\(\\d+,\\d+(?:,\\d+)?\\)"), "")
            .trim()
        // Use exact lineStartTime for both startMs and endMs.
        // This ensures the entire line highlights instantly as a single block when active,
        // matching ordinary LRC lyrics behavior perfectly and avoiding continuous sweep updates.
        syllables.add(Syllable(cleanText, lineStartTime, lineStartTime))
    }

    return syllables
}
