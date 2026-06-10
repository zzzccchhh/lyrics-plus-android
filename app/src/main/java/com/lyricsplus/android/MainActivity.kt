package com.lyricsplus.android

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import com.lyricsplus.android.analytics.AnonymousStats
import com.lyricsplus.android.spotify.SpotifyBroadcasts
import com.lyricsplus.android.spotify.LyricsSyncService
import com.lyricsplus.android.ui.LyricsPlusApp
import com.lyricsplus.android.ui.LyricsWebController
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var webController: LyricsWebController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        AnonymousStats.trackAppOpen(this)

        // Request 120Hz high refresh rate natively for fluid animations on VRR screens
        val windowParams = window.attributes
        windowParams.preferredRefreshRate = 120f
        window.attributes = windowParams

        // Edge-to-edge: let the app render behind system bars and cutouts
        // so Compose and WebView can handle safe area insets themselves.
        enableEdgeToEdge()

        webController = LyricsWebController(this)

        val overlay = ComposeView(this).apply {
            setContent {
                LyricsPlusApp(
                    viewModel = viewModel,
                    webController = webController,
                    onOpenSpotify = ::openSpotify,
                    onOpenNotificationAccess = ::openNotificationAccess
                )
            }
        }

        setContentView(overlay)
    }

    override fun onDestroy() {
        if (::webController.isInitialized) {
            webController.destroy()
        }
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, LyricsSyncService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncNow()
    }

    private fun openSpotify() {
        val launchIntent = packageManager.getLaunchIntentForPackage(SpotifyBroadcasts.PACKAGE_NAME)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${SpotifyBroadcasts.PACKAGE_NAME}")))
        }
    }

    private fun openNotificationAccess() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }
}
