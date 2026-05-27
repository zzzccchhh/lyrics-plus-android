package com.lyricsplus.android.spotify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lyricsplus.android.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LyricsSyncService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    
    private val mediaSessionReader by lazy { SpotifyMediaSessionReader(this) }
    
    private val spotifyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch {
                delay(250)
                syncMediaSession()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        
        val filter = IntentFilter().apply {
            addAction(SpotifyBroadcasts.METADATA_CHANGED)
            addAction(SpotifyBroadcasts.PLAYBACK_STATE_CHANGED)
            addAction(SpotifyBroadcasts.QUEUE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            spotifyReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        
        Log.d("LyricsSyncService", "Foreground service started and registered receiver.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(spotifyReceiver)
        scope.cancel()
        Log.d("LyricsSyncService", "Foreground service stopped.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun syncMediaSession() {
        val snapshot = mediaSessionReader.readSnapshot()
        if (snapshot != null) {
            LyricsNotificationListenerService.latestSnapshot = snapshot
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lyrics Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps lyrics synchronized in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lyrics Plus")
            .setContentText("Syncing with Spotify in background")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lyrics_sync_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
