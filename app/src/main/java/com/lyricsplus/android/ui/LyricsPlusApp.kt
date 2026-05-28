package com.lyricsplus.android.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import android.view.ViewGroup
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.lyricsplus.android.LyricsUiState
import com.lyricsplus.android.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.max
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight

private val AppBackground = Color(0xFF101010)
private val Accent = Color(0xFF4AD295)
private val Panel = Color(0xFF181A19)

@Composable
fun LyricsPlusApp(
    viewModel: MainViewModel,
    webController: LyricsWebController,
    onOpenSpotify: () -> Unit,
    onOpenNotificationAccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    MaterialTheme {
        LyricsOverlay(
            state = uiState,
            webController = webController,
            viewModel = viewModel,
            onOpenSpotify = onOpenSpotify,
            onOpenNotificationAccess = onOpenNotificationAccess
        )
    }
}

@Composable
private fun LyricsOverlay(
    state: LyricsUiState,
    webController: LyricsWebController,
    viewModel: MainViewModel,
    onOpenSpotify: () -> Unit,
    onOpenNotificationAccess: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isMultiPane = isLandscape || (configuration.screenWidthDp >= 600)

    val view = LocalView.current
    DisposableEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }
    val uriHandler = LocalUriHandler.current
    val isRightAligned = isMultiPane

    LaunchedEffect(webController.isReady, state.nowPlaying) {
        webController.pushTrack(state.nowPlaying)
    }

    LaunchedEffect(webController.isReady, state.lyrics) {
        webController.pushLyrics(state.lyrics)
    }

    LaunchedEffect(webController.isReady, state.lyrics) {
        webController.pushPlayback(state.playback.currentPositionMs() + state.lyricsOffsetMs, state.playback.isPlaying)
    }

    LaunchedEffect(webController.isReady, state.playback, state.lyricsOffsetMs) {
        webController.pushPlayback(state.playback.currentPositionMs() + state.lyricsOffsetMs, state.playback.isPlaying)
    }

    LaunchedEffect(webController.isReady, state.showRomaji) {
        webController.pushRomajiState(state.showRomaji)
    }

    LaunchedEffect(webController.isReady, isRightAligned) {
        webController.pushRightAligned(isRightAligned)
    }

    // Push safe area insets (system bars + display cutout) to the WebView
    // Use ViewCompat to get actual insets including display cutout (camera notch)
    val safeInsetConfig = remember(configuration) {
        configuration.orientation // trigger recomposition on rotation
    }
    LaunchedEffect(webController.isReady, safeInsetConfig) {
        val webView = webController.webView
        val density = webView.context.resources.displayMetrics.density

        // Try to get actual window insets via ViewCompat (includes display cutout)
        val windowInsets = androidx.core.view.ViewCompat.getRootWindowInsets(webView)
        if (windowInsets != null) {
            val typeMask = androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            val insets = windowInsets.getInsets(typeMask)
            val topDp = if (density > 0) (insets.top / density).toInt() else 28
            val rightDp = if (density > 0) (insets.right / density).toInt() else 0
            val bottomDp = if (density > 0) (insets.bottom / density).toInt() else 0
            val leftDp = if (density > 0) (insets.left / density).toInt() else 0
            webController.pushSafeInsets(topDp, rightDp, bottomDp, leftDp)
        } else {
            // Fallback: use system resource values
            val resources = webView.context.resources
            val statusBarResId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarPx = if (statusBarResId > 0) resources.getDimensionPixelSize(statusBarResId) else 0
            val statusBarDp = if (density > 0) (statusBarPx / density).toInt() else 28
            webController.pushSafeInsets(statusBarDp, 0, 0, 0)
        }
    }

    var isExpanded by remember { mutableStateOf(false) }
    var isButtonVisible by remember { mutableStateOf(false) }
    var playbackButtonsShowTrigger by remember { mutableStateOf(0) }
    var headerHeightPx by remember { mutableStateOf(0) }
    var showOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(state.lyrics.isEmpty(), state.nowPlaying.track) {
        if (state.lyrics.isEmpty()) {
            showOverlay = false // Reset showOverlay immediately to prevent flashing stale states during cache lookup
            delay(150) // Delay by 150ms to allow local SQLite/in-memory cache to load first
            showOverlay = true
        } else {
            showOverlay = false
        }
    }

    LaunchedEffect(webController.isReady, isMultiPane, headerHeightPx) {
        if (isMultiPane) {
            webController.pushHeaderHeight(0)
        } else {
            webController.pushHeaderHeight(headerHeightPx)
        }
    }

    LaunchedEffect(isButtonVisible, isExpanded) {
        if (isButtonVisible && !isExpanded) {
            delay(4000)
            isButtonVisible = false
        }
    }

    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            isButtonVisible = false
        }
    }

    LaunchedEffect(playbackButtonsShowTrigger) {
        if (playbackButtonsShowTrigger > 0) {
            delay(4000)
            playbackButtonsShowTrigger = 0
        }
    }

    LaunchedEffect(state.nowPlaying.track) {
        if (state.nowPlaying.hasTrack) {
            playbackButtonsShowTrigger++
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Host WebView natively inside Compose container
        AndroidView(
            factory = {
                val webView = webController.webView
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webView
            },
            modifier = Modifier.fillMaxSize()
        )

        val hasTrack = state.nowPlaying.hasTrack
        val isInitializing = state.isInitializing
        if (!isInitializing && (!hasTrack || (state.lyrics.isEmpty() && showOverlay))) {
            EmptyOverlay(
                state = state,
                onOpenSpotify = onOpenSpotify,
                onOpenNotificationAccess = onOpenNotificationAccess,
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBackground)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(28.dp)
            )
        } else if (webController.debugMessage.startsWith("error:", ignoreCase = true)) {
            DebugOverlay(
                message = webController.debugMessage,
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBackground)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(28.dp)
            )
        }

        if (webController.debugMessage.startsWith("error:", ignoreCase = true)) {
            DebugChip(
                message = webController.debugMessage,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(10.dp)
            )
        }

        // PREMIUM TOP INTEGRATED INFO & CONTROLLER HEADER BAR / LEFT SPLIT BAR
        if (state.nowPlaying.hasTrack && !webController.isFullLyricsMode) {
            if (isMultiPane) {
                // Split-Screen layout overlay
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Left Column: Album art + info as one centered visual group
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AlbumArtView(
                            bitmap = state.nowPlaying.albumArt,
                            startColorHex = state.nowPlaying.backgroundStart,
                            endColorHex = state.nowPlaying.backgroundEnd,
                            modifier = Modifier.size(150.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = state.nowPlaying.track,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 24.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.nowPlaying.artist,
                            color = Color(0xB3FFFFFF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Playback controls row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayPauseButton(
                                isPlaying = state.playback.isPlaying,
                                onClick = { viewModel.togglePlayback() }
                            )

                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .width(1.dp)
                                    .background(Color(0x26FFFFFF))
                            )

                            SkipNextButton(
                                onClick = { viewModel.skipToNext() }
                            )
                        }
                    }

                    // Right weight (WebView lyrics show and respond to touches underneath)
                    Spacer(modifier = Modifier.weight(0.55f))
                }
            } else {
                // Portrait Overlay Layout
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            if (playbackButtonsShowTrigger > 0) {
                                playbackButtonsShowTrigger = 0
                            } else {
                                playbackButtonsShowTrigger++
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                        .onGloballyPositioned { coordinates ->
                            val h = coordinates.size.height
                            if (h != headerHeightPx) {
                                headerHeightPx = h
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = state.nowPlaying.track,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 36.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.nowPlaying.artist,
                            color = Color(0xB3FFFFFF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    AnimatedVisibility(
                        visible = playbackButtonsShowTrigger > 0 || !state.playback.isPlaying,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayPauseButton(
                                isPlaying = state.playback.isPlaying,
                                onClick = {
                                    playbackButtonsShowTrigger++
                                    viewModel.togglePlayback()
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .width(1.dp)
                                    .background(Color(0x26FFFFFF))
                            )

                            SkipNextButton(
                                onClick = {
                                    playbackButtonsShowTrigger++
                                    viewModel.skipToNext()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        isExpanded = false
                    }
            )
        }

        // Invisible Touch Hotspot Box (Auto-show button when tapped)
        Box(
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.BottomEnd)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    enabled = !isExpanded
                ) {
                    isButtonVisible = true
                }
        )

        val offsetSec = state.lyricsOffsetMs / 1000.0
        val offsetText = if (state.lyricsOffsetMs > 0) "+${offsetSec}s" else if (state.lyricsOffsetMs < 0) "${offsetSec}s" else "0.0s"

        // FLOATING CONTROLS MENU
        AnimatedVisibility(
            visible = isButtonVisible || isExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (isLandscape) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xEE161A18), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MenuActionRow(label = "立刻同步", emoji = "🔄") {
                                        viewModel.forceSyncTime()
                                    }
                                    MenuActionRow(label = "歌词提前 (-0.5s) [当前: $offsetText]", emoji = "🐰") {
                                        viewModel.adjustOffset(-500)
                                    }
                                    MenuActionRow(label = "歌词延时 (+0.5s) [当前: $offsetText]", emoji = "🐢") {
                                        viewModel.adjustOffset(500)
                                    }
                                    MenuActionRow(label = "重新取色", emoji = "🎨") {
                                        viewModel.rotatePaletteColors()
                                    }
                                    MenuActionRow(label = "切换歌词源 [当前: ${state.activeLyricsSource}]", emoji = "🎵") {
                                        viewModel.switchLyricsSource()
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MenuActionRow(
                                        label = if (state.showRomaji) "罗马音: 开启" else "罗马音: 关闭",
                                        emoji = "🔤",
                                        active = state.showRomaji
                                    ) {
                                        viewModel.toggleRomaji()
                                    }
                                    MenuActionRow(
                                        label = if (state.keepScreenOn) "屏幕常亮: 开启" else "屏幕常亮: 关闭",
                                        emoji = "💡",
                                        active = state.keepScreenOn
                                    ) {
                                        viewModel.toggleKeepScreenOn()
                                    }
                                    MenuActionRow(label = "检查更新", emoji = "🚀") {
                                        viewModel.checkForUpdates()
                                    }
                                    MenuActionRow(label = "项目地址(欢迎Star)", emoji = "⭐") {
                                        uriHandler.openUri("https://github.com/Artriai/lyrics-plus-android")
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MenuActionRow(label = "立刻同步", emoji = "🔄") {
                                viewModel.forceSyncTime()
                            }
                            MenuActionRow(label = "歌词提前 (-0.5s) [当前: $offsetText]", emoji = "🐰") {
                                viewModel.adjustOffset(-500)
                            }
                            MenuActionRow(label = "歌词延时 (+0.5s) [当前: $offsetText]", emoji = "🐢") {
                                viewModel.adjustOffset(500)
                            }
                            MenuActionRow(label = "重新取色", emoji = "🎨") {
                                viewModel.rotatePaletteColors()
                            }
                            MenuActionRow(label = "切换歌词源 [当前: ${state.activeLyricsSource}]", emoji = "🎵") {
                                viewModel.switchLyricsSource()
                            }
                            MenuActionRow(
                                label = if (state.showRomaji) "罗马音: 开启" else "罗马音: 关闭",
                                emoji = "🔤",
                                active = state.showRomaji
                            ) {
                                viewModel.toggleRomaji()
                            }
                            MenuActionRow(
                                label = if (state.keepScreenOn) "屏幕常亮: 开启" else "屏幕常亮: 关闭",
                                emoji = "💡",
                                active = state.keepScreenOn
                            ) {
                                viewModel.toggleKeepScreenOn()
                            }
                            MenuActionRow(label = "检查更新", emoji = "🚀") {
                                viewModel.checkForUpdates()
                            }
                            MenuActionRow(label = "项目地址(欢迎Star)", emoji = "⭐") {
                                uriHandler.openUri("https://github.com/Artriai/lyrics-plus-android")
                            }
                        }
                    }
                }

                // Main Toggle FAB Button (Minimized presence ghost button)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (isExpanded) Accent else Color(0x22FFFFFF), CircleShape)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { isExpanded = !isExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isExpanded) "✕" else "⚙",
                        color = if (isExpanded) Color.Black else Color(0x66FFFFFF),
                        fontSize = if (isExpanded) 16.sp else 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugChip(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message.take(90),
        color = Color.White,
        fontSize = 11.sp,
        modifier = modifier
            .background(Color(0x99000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun DebugOverlay(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Lyrics stage debug",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            color = Color(0xFF8D9490),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun EmptyOverlay(
    state: LyricsUiState,
    onOpenSpotify: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasTrack = state.nowPlaying.hasTrack
    val isLoading = state.isLoadingLyrics

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (hasTrack && !isLoading) {
            // Instrumental / no lyrics found state — clean centered display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "♪",
                    color = Color(0x66FFFFFF),
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "纯音乐 / 无歌词",
                    color = Color(0xB3FFFFFF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${state.nowPlaying.track} - ${state.nowPlaying.artist}",
                    color = Color(0x66FFFFFF),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else if (hasTrack && isLoading) {
            // Loading lyrics state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "正在搜索歌词...",
                    color = Color(0xB3FFFFFF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${state.nowPlaying.track} - ${state.nowPlaying.artist}",
                    color = Color(0x66FFFFFF),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            // No track playing — setup/welcome state
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = state.message,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 36.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${state.playbackSource} - ${state.playback.positionMs / 1000}s",
                    color = Color(0xFF8D9490),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onOpenSpotify,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Open Spotify")
                    }
                    Button(
                        onClick = onOpenNotificationAccess,
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Notification Access")
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuActionRow(
    label: String,
    emoji: String,
    active: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xD9101211), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .background(if (active) Color(0xD9323634) else Color(0x66323634), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            Canvas(modifier = Modifier.size(24.dp)) {
                val barWidth = size.width * 0.18f
                val gap = size.width * 0.22f
                val h = size.height * 0.55f
                val startY = size.height * 0.225f
                
                // Left bar
                drawRect(
                    color = Color.White,
                    topLeft = Offset(size.width * 0.26f, startY),
                    size = Size(barWidth, h)
                )
                // Right bar
                drawRect(
                    color = Color.White,
                    topLeft = Offset(size.width * 0.26f + barWidth + gap, startY),
                    size = Size(barWidth, h)
                )
            }
        } else {
            Canvas(modifier = Modifier.size(24.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.32f, size.height * 0.22f)
                    lineTo(size.width * 0.82f, size.height * 0.5f)
                    lineTo(size.width * 0.32f, size.height * 0.78f)
                    close()
                }
                drawPath(path, color = Color.White)
            }
        }
    }
}

@Composable
private fun SkipNextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.24f)
                lineTo(size.width * 0.64f, size.height * 0.5f)
                lineTo(size.width * 0.22f, size.height * 0.76f)
                close()
            }
            drawPath(path, color = Color.White)
            
            drawRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.69f, size.height * 0.24f),
                size = Size(size.width * 0.12f, size.height * 0.52f)
            )
        }
    }
}

@Composable
private fun AlbumArtView(
    bitmap: android.graphics.Bitmap?,
    startColorHex: String?,
    endColorHex: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .background(Color(0xFF181A19), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Album Art",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            val startColor = startColorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Color(0xFF2C3E50)
            val endColor = endColorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Color(0xFF101010)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(startColor, endColor)
                        ),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎵",
                    fontSize = 42.sp
                )
            }
        }
    }
}
