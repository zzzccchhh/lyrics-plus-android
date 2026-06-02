package com.lyricsplus.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
import androidx.compose.ui.composed
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
    val presets = remember { listOf("#FFFFFF", "#4AD295", "#F06AA5", "#3FA9F5") }
    var showColorPicker by remember { mutableStateOf(service.textColorHex !in presets) }

    var showControls by remember { mutableStateOf(true) }
    var interactionTrigger by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    var tapCount by remember { mutableStateOf(0) }
    var tapJob by remember { mutableStateOf<Job?>(null) }

    // Auto-hide controls after 2 seconds of inactivity
    androidx.compose.runtime.LaunchedEffect(interactionTrigger) {
        showControls = true
        kotlinx.coroutines.delay(2000)
        showControls = false
    }

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
        // Main floating bar — drag gesture covers the entire bar surface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE6101211).copy(alpha = backgroundOpacity))
                .border(1.dp, if (backgroundOpacity > 0.01f) Color(0x0DFFFFFF) else Color.Transparent, RoundedCornerShape(14.dp))
                .then(
                    if (!isLocked) {
                        Modifier
                            .pointerInput(isLocked) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)
                                    interactionTrigger++ // Reset timer on drag
                                }
                            }
                            .pointerInput(isLocked) {
                                detectTapGestures(
                                    onTap = {
                                        tapCount++
                                        tapJob?.cancel()
                                        tapJob = coroutineScope.launch {
                                            delay(280)
                                            if (tapCount == 1) {
                                                interactionTrigger++ // Show controls on tap
                                            } else if (tapCount == 2) {
                                                service.togglePlayback()
                                                interactionTrigger++ // Reset timer
                                            } else if (tapCount >= 3) {
                                                service.nextTrack()
                                                interactionTrigger++ // Reset timer
                                            }
                                            tapCount = 0
                                        }
                                    }
                                )
                            }
                    } else {
                        Modifier.pointerInput(isLocked) {
                            detectTapGestures(
                                onTap = {
                                    tapCount++
                                    tapJob?.cancel()
                                    tapJob = coroutineScope.launch {
                                        delay(280)
                                        if (tapCount == 1) {
                                            interactionTrigger++ // Show controls on tap
                                        } else if (tapCount == 2) {
                                            service.togglePlayback()
                                            interactionTrigger++ // Reset timer
                                        } else if (tapCount >= 3) {
                                            service.nextTrack()
                                            interactionTrigger++ // Reset timer
                                        }
                                        tapCount = 0
                                    }
                                }
                            )
                        }
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lyric active line presentation (Hardware accelerated sweep)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                ActiveLyricPresenter(
                    service = service,
                    textSizeSp = textSizeSp,
                    textColor = textColor
                )
            }

            // Control icons - animated fade out with premium monochrome translucent styling
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isLocked) {
                        // Locked: show a small unlock icon
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF)) // Misty white background
                                .noRippleClickable {
                                    service.isLocked = false
                                    interactionTrigger++
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "解锁",
                                modifier = Modifier.size(12.dp),
                                tint = Color.White
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Lock button
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF)) // Misty white background
                                    .noRippleClickable {
                                        service.isLocked = true
                                        interactionTrigger++
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "锁定",
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White
                                )
                            }

                            // Settings toggle
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isExpanded) Color(0x66FFFFFF) else Color(0x33FFFFFF)
                                    )
                                    .noRippleClickable {
                                        isExpanded = !isExpanded
                                        interactionTrigger++
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Rounded.Close else Icons.Rounded.Settings,
                                    contentDescription = "设置",
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White
                                )
                            }

                            // Close button
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF)) // Misty white background
                                    .noRippleClickable {
                                        onClose()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expanded Settings Panel - Instant toggle avoids expensive frame-by-frame WindowManager layout updates
        if (isExpanded && !isLocked) {
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
                                .noRippleClickable {
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
                                .noRippleClickable {
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
                                .noRippleClickable {
                                    if (service.backgroundOpacity > 0.05f) {
                                        service.backgroundOpacity = (service.backgroundOpacity - 0.1f).coerceAtLeast(0f)
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
                                .noRippleClickable {
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val colorsMap = mapOf(
                            "#FFFFFF" to Color.White,
                            "#4AD295" to Color(0xFF4AD295),
                            "#F06AA5" to Color(0xFFF06AA5),
                            "#3FA9F5" to Color(0xFF3FA9F5)
                        )
                        // Render presets
                        presets.forEach { hex ->
                            val color = colorsMap[hex] ?: Color.White
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        width = if (service.textColorHex == hex) 2.dp else 0.dp,
                                        color = if (service.textColorHex == hex) Color.Black else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .noRippleClickable {
                                        showColorPicker = false
                                        service.textColorHex = hex
                                        service.savePrefs()
                                    }
                            )
                        }

                        // Custom color picker toggle button (palette emoji/rainbow background)
                        val isCustomSelected = service.textColorHex !in presets
                        val customColor = if (isCustomSelected) textColor else Color(0xFFE040FB)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(customColor, CircleShape)
                                .border(
                                    width = if (isCustomSelected) 2.dp else 0.dp,
                                    color = if (isCustomSelected) Color.Black else Color.Transparent,
                                    shape = CircleShape
                                )
                                .noRippleClickable {
                                    showColorPicker = !showColorPicker
                                    if (!isCustomSelected) {
                                        // Default custom color (e.g. vivid pink/orange)
                                        service.textColorHex = "#FF55AA"
                                        service.savePrefs()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🎨",
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Inline Custom Color Pick Sliders (R, G, B)
                if (showColorPicker) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp)
                            .background(Color(0x13FFFFFF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val hex = service.textColorHex.removePrefix("#")
                        val currentR = runCatching { hex.substring(0, 2).toInt(16) }.getOrDefault(255)
                        val currentG = runCatching { hex.substring(2, 4).toInt(16) }.getOrDefault(255)
                        val currentB = runCatching { hex.substring(4, 6).toInt(16) }.getOrDefault(255)

                        ColorSliderRow(label = "红", value = currentR, color = Color(0xFFFF5252)) { newR ->
                            val newHex = String.format("#%02X%02X%02X", newR, currentG, currentB)
                            service.textColorHex = newHex
                            service.savePrefs()
                        }
                        ColorSliderRow(label = "绿", value = currentG, color = Color(0xFF66BB6A)) { newG ->
                            val newHex = String.format("#%02X%02X%02X", currentR, newG, currentB)
                            service.textColorHex = newHex
                            service.savePrefs()
                        }
                        ColorSliderRow(label = "蓝", value = currentB, color = Color(0xFF29B6F6)) { newB ->
                            val newHex = String.format("#%02X%02X%02X", currentR, currentG, newB)
                            service.textColorHex = newHex
                            service.savePrefs()
                        }
                    }
                }

                // Row 4: Display Mode Selection (Original / Translation)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "显示内容", color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modes = listOf("原文", "翻译")
                        modes.forEachIndexed { index, name ->
                            val isSelected = service.displayMode == index
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) Color(0xFF4AD295) else Color(0x33FFFFFF),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .noRippleClickable {
                                        service.displayMode = index
                                        service.savePrefs()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveLyricPresenter(
    service: FloatingLyricsService,
    textSizeSp: Float,
    textColor: Color
) {
    val track = service.currentTrack
    val lyrics = service.currentLyrics
    val estimatedTime = service.estimatedPositionMs + service.lyricsOffsetMs
    
    val activeIndex = findActiveIndex(estimatedTime, lyrics)
    val activeLine = lyrics.getOrNull(activeIndex)

    if (track == null) {
        Text(
            text = "等待 Spotify 播放音乐...",
            color = Color(0xB3FFFFFF),
            fontSize = textSizeSp.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    } else if (lyrics.isNotEmpty() && estimatedTime < lyrics[0].startTimeMs) {
        // Intro phase: show song title and artist in high visibility active text color
        Text(
            text = "${track.track} - ${track.artist}",
            color = textColor,
            fontSize = textSizeSp.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    } else if (activeLine == null) {
        Text(
            text = "纯音乐 / 无歌词",
            color = Color(0xB3FFFFFF),
            fontSize = textSizeSp.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        val activeText = when (service.displayMode) {
            1 -> if (!activeLine.translation.isNullOrBlank()) activeLine.translation else activeLine.text
            2 -> if (!activeLine.reading.isNullOrBlank()) activeLine.reading else activeLine.text
            else -> activeLine.text
        }

        val syllables = remember(activeText, activeLine.startTimeMs) {
            parseSyllables(activeText, activeLine.startTimeMs)
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            maxLines = 2
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
    if (positionMs < lyrics[0].startTimeMs) return -1
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

@Composable
fun ColorSliderRow(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xB3FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.5f),
                inactiveTrackColor = Color(0x22FFFFFF)
            )
        )
        Text(text = String.format("%02X", value), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
    }
}

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

