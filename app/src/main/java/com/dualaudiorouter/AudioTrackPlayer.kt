package com.example.dualaudiorouter

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException

/**
 * Audio player using MediaPlayer with device routing support
 * Properly handles encoded audio files (MP3, WAV, AAC, etc.)
 */
class AudioTrackPlayer(
    private val context: Context,
    private val trackName: String
) {
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var isPaused = false
    private var currentUri: Uri? = null
    private val handler = Handler(Looper.getMainLooper())

    private var onProgressUpdate: ((Int, Int) -> Unit)? = null
    private var onPlaybackComplete: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    // Progress tracking
    private var progressRunnable: Runnable? = null

    /**
     * Load audio file from URI
     */
    fun loadAudioFile(uri: Uri): Boolean {
        return try {
            currentUri = uri
            Log.d(TAG, "$trackName: Audio file URI saved: $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error saving audio URI", e)
            onError?.invoke("Error loading file: ${e.message}")
            false
        }
    }

    /**
     * Prepare MediaPlayer with specified device
     */
    fun prepareAudioTrack(targetDevice: AudioDevice?): Boolean {
        try {
            val uri = currentUri ?: return false

            // Release any existing MediaPlayer
            release()

            // Create new MediaPlayer
            mediaPlayer = MediaPlayer().apply {

                // Set audio attributes
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )

                // Set data source
                setDataSource(context, uri)

                // Set preferred device for routing (API 23+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    targetDevice?.deviceInfo?.let { deviceInfo ->
                        val success = setPreferredDevice(deviceInfo)
                        Log.d(TAG, "$trackName: Set preferred device ${deviceInfo.productName}: $success")

                        if (!success) {
                            Log.w(TAG, "$trackName: Failed to set preferred device, using default")
                        }
                    }
                }

                // Set completion listener
                setOnCompletionListener {
                    Log.d(TAG, "$trackName: Playback completed")
                    //isPlaying = false
                    isPaused = false
                    stopProgressTracking()
                    handler.post {
                        onPlaybackComplete?.invoke()
                    }
                }

                // Set error listener
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "$trackName: MediaPlayer error: what=$what, extra=$extra")
                    handler.post {
                        onError?.invoke("Playback error: $what")
                    }
                    true // Indicate we handled the error
                }

                // Prepare synchronously
                prepare()
            }

            Log.d(TAG, "$trackName: MediaPlayer prepared successfully")
            return true

        } catch (e: IOException) {
            Log.e(TAG, "$trackName: IO error preparing MediaPlayer", e)
            onError?.invoke("Cannot play this audio format")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error preparing MediaPlayer", e)
            onError?.invoke("Error preparing audio: ${e.message}")
            return false
        }
    }

    /**
     * Start playback
     */
    fun play() {
        val player = mediaPlayer
        if (player == null) {
            onError?.invoke("MediaPlayer not prepared")
            return
        }

        try {
            if (isPaused) {
                // Resume from pause
                player.start()
                isPaused = false
            } else {
                // Start from beginning
                player.seekTo(0)
                player.start()
            }

            isPlaying = true
            startProgressTracking()

            Log.d(TAG, "$trackName: Playback started")

        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error starting playback", e)
            onError?.invoke("Error starting playback: ${e.message}")
        }
    }

    /**
     * Pause playback
     */
    fun pause() {
        val player = mediaPlayer
        if (player == null || !isPlaying || isPaused) return

        try {
            player.pause()
            isPaused = true
            stopProgressTracking()
            Log.d(TAG, "$trackName: Playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error pausing playback", e)
        }
    }

    /**
     * Resume playback
     */
    fun resume() {
        if (!isPaused) return
        play() // This will handle resuming
    }

    /**
     * Stop playback
     */
    fun stop() {
        val player = mediaPlayer ?: return

        try {
            if (isPlaying) {
                player.stop()
                player.prepare() // Re-prepare for next play
            }

            isPlaying = false
            isPaused = false
            stopProgressTracking()

            Log.d(TAG, "$trackName: Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error stopping playback", e)
        }
    }

    /**
     * Release resources
     */
    fun release() {
        stop()
        stopProgressTracking()

        mediaPlayer?.release()
        mediaPlayer = null
        currentUri = null

        Log.d(TAG, "$trackName: Resources released")
    }

    /**
     * Start progress tracking
     */
    private fun startProgressTracking() {
        stopProgressTracking()

        progressRunnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer
                if (player != null && isPlaying && !isPaused) {
                    try {
                        val currentPosition = player.currentPosition
                        val duration = player.duration

                        handler.post {
                            onProgressUpdate?.invoke(currentPosition, duration)
                        }

                        // Schedule next update
                        handler.postDelayed(this, 100) // Update every 100ms
                    } catch (e: Exception) {
                        Log.w(TAG, "$trackName: Error getting playback position", e)
                    }
                }
            }
        }

        progressRunnable?.let {
            handler.post(it)
        }
    }

    /**
     * Stop progress tracking
     */
    private fun stopProgressTracking() {
        progressRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
        }
        progressRunnable = null
    }

    /**
     * Set progress update callback
     */
    fun setOnProgressUpdateListener(listener: (Int, Int) -> Unit) {
        onProgressUpdate = listener
    }

    /**
     * Set playback complete callback
     */
    fun setOnPlaybackCompleteListener(listener: () -> Unit) {
        onPlaybackComplete = listener
    }

    /**
     * Set error callback
     */
    fun setOnErrorListener(listener: (String) -> Unit) {
        onError = listener
    }

    /**
     * Get current playback state - RENAMED FUNCTIONS TO AVOID CONFLICT
     */
    fun isCurrentlyPlaying(): Boolean = isPlaying && !isPaused
    fun isCurrentlyPaused(): Boolean = isPaused

    companion object {
        private const val TAG = "AudioTrackPlayer"
    }
}
