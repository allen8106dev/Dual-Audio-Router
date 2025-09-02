package com.example.dualaudiorouter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Background service for audio playback persistence
 * Keeps audio playing when app is in background
 */
class AudioPlaybackService : Service() {

    private val binder = AudioPlaybackBinder()
    private var isServiceRunning = false

    // Reference to players from MainActivity
    var playerA: AudioTrackPlayer? = null
    var playerB: AudioTrackPlayer? = null

    // Playback state
    private var isPlaying = false
    private var currentTrackAName = ""
    private var currentTrackBName = ""

    inner class AudioPlaybackBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AudioPlaybackService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AudioPlaybackService started")

        when (intent?.action) {
            ACTION_PLAY -> handlePlayAction()
            ACTION_PAUSE -> handlePauseAction()
            ACTION_STOP -> handleStopAction()
            ACTION_START_PLAYBACK -> startForegroundPlayback()
        }

        return START_STICKY // Restart service if killed by system
    }

    /**
     * Start foreground service with notification
     */
    private fun startForegroundPlayback() {
        if (!isServiceRunning) {
            val notification = createPlaybackNotification()
            startForeground(NOTIFICATION_ID, notification)
            isServiceRunning = true
            isPlaying = true
            Log.d(TAG, "Started foreground service")
        }
    }

    /**
     * Handle play action from notification
     */
    private fun handlePlayAction() {
        playerA?.resume()
        playerB?.resume()
        isPlaying = true
        updateNotification()
        Log.d(TAG, "Play action handled")
    }

    /**
     * Handle pause action from notification
     */
    private fun handlePauseAction() {
        playerA?.pause()
        playerB?.pause()
        isPlaying = false
        updateNotification()
        Log.d(TAG, "Pause action handled")
    }

    /**
     * Handle stop action from notification
     */
    private fun handleStopAction() {
        playerA?.stop()
        playerB?.stop()
        isPlaying = false
        stopForegroundService()
        Log.d(TAG, "Stop action handled")
    }

    /**
     * Set player references from MainActivity
     */
    fun setPlayers(playerA: AudioTrackPlayer, playerB: AudioTrackPlayer) {
        this.playerA = playerA
        this.playerB = playerB
        Log.d(TAG, "Players set in service")
    }

    /**
     * Update track information
     */
    fun updateTrackInfo(trackAName: String, trackBName: String) {
        currentTrackAName = trackAName
        currentTrackBName = trackBName
        if (isServiceRunning) {
            updateNotification()
        }
    }

    /**
     * Update playback state
     */
    fun updatePlaybackState(playing: Boolean) {
        isPlaying = playing
        if (isServiceRunning) {
            updateNotification()
        }
    }

    /**
     * Stop the foreground service
     */
    fun stopForegroundService() {
        if (isServiceRunning) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isServiceRunning = false
            stopSelf()
            Log.d(TAG, "Stopped foreground service")
        }
    }

    /**
     * Create notification channel for Android 8+
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Dual Audio Router playback controls"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Create playback notification with controls - FIXED VERSION
     */
    private fun createPlaybackNotification(): Notification {
        // Intent to open app when notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Play/Pause action
        val playPauseAction = if (isPlaying) {
            val pauseIntent = Intent(this, AudioPlaybackService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getService(
                this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            )
        } else {
            val playIntent = Intent(this, AudioPlaybackService::class.java).apply {
                action = ACTION_PLAY
            }
            val playPendingIntent = PendingIntent.getService(
                this, 2, playIntent, PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                playPendingIntent
            )
        }

        // Stop action
        val stopIntent = Intent(this, AudioPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        )

        // Build notification - FIXED MediaStyle usage
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dual Audio Router")
            .setContentText(getPlaybackText())
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .build()
    }

    /**
     * Update existing notification with permission check
     */
    private fun updateNotification() {
        if (isServiceRunning) {
            // NEW: Check notification permission before updating
            if (hasNotificationPermission()) {
                val notification = createPlaybackNotification()
                val notificationManager = NotificationManagerCompat.from(this)
                try {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to update notification: ${e.message}")
                }
            } else {
                Log.w(TAG, "No notification permission, cannot update notification")
            }
        }
    }

    /**
     * Check if app has notification permission
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires explicit notification permission
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 13: Check if notifications are enabled
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
    }

    /**
     * Get playback status text for notification
     */
    private fun getPlaybackText(): String {
        return when {
            currentTrackAName.isNotEmpty() && currentTrackBName.isNotEmpty() -> {
                if (isPlaying) "Playing: $currentTrackAName + $currentTrackBName"
                else "Paused: $currentTrackAName + $currentTrackBName"
            }
            currentTrackAName.isNotEmpty() -> {
                if (isPlaying) "Playing: $currentTrackAName" else "Paused: $currentTrackAName"
            }
            else -> {
                if (isPlaying) "Playing dual audio" else "Paused"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AudioPlaybackService destroyed")
        playerA?.release()
        playerB?.release()
    }

    companion object {
        private const val TAG = "AudioPlaybackService"
        private const val CHANNEL_ID = "AudioPlaybackChannel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.example.dualaudiorouter.PLAY"
        const val ACTION_PAUSE = "com.example.dualaudiorouter.PAUSE"
        const val ACTION_STOP = "com.example.dualaudiorouter.STOP"
        const val ACTION_START_PLAYBACK = "com.example.dualaudiorouter.START_PLAYBACK"
    }
}
