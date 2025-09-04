package com.example.dualaudiorouter

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

class AudioTrackPlayer(
    private val context: Context,
    private val trackName: String
) {
    private var mediaPlayer: MediaPlayer? = null
    private var playingState = false
    private var pausedState = false
    private var audioUri: Uri? = null
    private val handler = Handler(Looper.getMainLooper())

    // Delay support
    private var delayMs: Long = 0

    // NEW: Position tracking for synchronization
    private var savedPosition: Int = 0

    // NEW: Volume control
    private var currentVolume: Float = 1.0f // Default to full volume
    private var onProgressUpdate: ((Int, Int) -> Unit)? = null
    private var onPlaybackComplete: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var progressRunnable: Runnable? = null

    fun loadAudioFile(uri: Uri): Boolean {
        return try {
            audioUri = uri
            Log.d(TAG, "$trackName: Audio file URI saved: $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error saving audio URI", e)
            onError?.invoke("Error loading file: ${e.message}")
            false
        }
    }

    // NEW: Set volume for this track (0.0 = silent, 1.0 = full volume)
    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0.0f, 1.0f)
        currentVolume = clampedVolume
        mediaPlayer?.setVolume(clampedVolume, clampedVolume)
        Log.d(TAG, "$trackName: Volume set to ${(clampedVolume * 100).toInt()}%")
    }

    // NEW: Get current volume
    fun getVolume(): Float = currentVolume

    // NEW: Set volume as percentage (0-100)
    fun setVolumePercent(percent: Int) {
        val volume = (percent.coerceIn(0, 100) / 100.0f)
        setVolume(volume)
    }

    // NEW: Get volume as percentage (0-100)
    fun getVolumePercent(): Int = (currentVolume * 100).toInt()

    // NEW: Mute/unmute toggle
    fun toggleMute(): Boolean {
        return if (currentVolume > 0.0f) {
            setVolume(0.0f)
            true // Now muted
        } else {
            setVolume(1.0f)
            false // Now unmuted
        }
    }

    // NEW: Check if muted
    fun isMuted(): Boolean = currentVolume == 0.0f
    // UPDATED: Apply volume when MediaPlayer is created
    fun prepareAudioTrack(targetDevice: AudioDevice?): Boolean {
        try {
            val uri = audioUri ?: return false
            release()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )

                setDataSource(context, uri)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    targetDevice?.deviceInfo?.let { deviceInfo ->
                        val success = setPreferredDevice(deviceInfo)
                        Log.d(TAG, "$trackName: Set preferred device success: $success")
                    }
                }

                setOnCompletionListener {
                    playingState = false
                    pausedState = false
                    stopProgressTracking()
                    handler.post { onPlaybackComplete?.invoke() }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "$trackName: MediaPlayer error: what=$what, extra=$extra")
                    handler.post { onError?.invoke("Playback error: $what") }
                    true
                }

                prepare()

                // NEW: Apply current volume setting to new MediaPlayer
                setVolume(currentVolume, currentVolume)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error preparing MediaPlayer", e)
            onError?.invoke("Error preparing audio: ${e.message}")
            return false
        }
    }

    // Set delay in milliseconds
    fun setDelay(delayMs: Long) {
        this.delayMs = delayMs
        Log.d(TAG, "$trackName: Delay set to ${delayMs}ms")
    }

    // NEW: Get current playback position
    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: savedPosition
        } catch (e: Exception) {
            Log.w(TAG, "$trackName: Could not get current position", e)
            savedPosition
        }
    }

    // NEW: Seek to specific position for synchronization
    fun seekToPosition(positionMs: Int) {
        val player = mediaPlayer ?: return

        try {
            savedPosition = positionMs
            player.seekTo(positionMs)
            Log.d(TAG, "$trackName: Seeked to position ${positionMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error seeking to $positionMs", e)
        }
    }

    // ENHANCED: Pause at specific position with better timing
    fun pauseAtPosition(positionMs: Int? = null) {
        val player = mediaPlayer
        if (player == null || !playingState || pausedState) return

        try {
            // Save current position or use provided position
            val targetPosition = positionMs ?: player.currentPosition

            // If we need to seek to a different position, do it first
            if (positionMs != null && positionMs != player.currentPosition) {
                player.setOnSeekCompleteListener { mp ->
                    // Pause after seek completes
                    mp.pause()
                    savedPosition = targetPosition
                    pausedState = true
                    playingState = false
                    stopProgressTracking()
                    Log.d(TAG, "$trackName: Paused at position ${targetPosition}ms after seek")

                    // Clear listener
                    mp.setOnSeekCompleteListener(null)
                }
                player.seekTo(targetPosition)
            } else {
                // Just pause at current position
                savedPosition = targetPosition
                player.pause()
                pausedState = true
                playingState = false
                stopProgressTracking()
                Log.d(TAG, "$trackName: Paused at position ${targetPosition}ms")
            }

        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error pausing at position", e)
        }
    }


    // UPDATED: Resume from specific position with proper seek synchronization
    fun resumeFromPosition(positionMs: Int? = null) {
        val player = mediaPlayer
        if (player == null || !pausedState) return

        try {
            val seekPosition = positionMs ?: savedPosition
            savedPosition = seekPosition

            // Set up seek completion listener BEFORE seeking
            player.setOnSeekCompleteListener { mp ->
                // This runs ONLY after seek actually completes
                try {
                    mp.start()
                    pausedState = false
                    playingState = true
                    startProgressTracking()
                    Log.d(TAG, "$trackName: Resumed from position ${seekPosition}ms after seek completion")

                    // Clear the listener to avoid memory leaks
                    mp.setOnSeekCompleteListener(null)
                } catch (e: Exception) {
                    Log.e(TAG, "$trackName: Error starting after seek completion", e)
                    onError?.invoke("Error resuming playback: ${e.message}")
                }
            }

            // Now perform the seek - playback will start automatically when done
            player.seekTo(seekPosition)
            Log.d(TAG, "$trackName: Seeking to position ${seekPosition}ms, will start when complete")

        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error setting up resume from position", e)
            onError?.invoke("Error resuming playback: ${e.message}")
        }
    }


    // Play with delay support
    fun play() {
        val player = mediaPlayer ?: return

        if (delayMs > 0 && !pausedState) {
            Log.d(TAG, "$trackName: Starting playback with ${delayMs}ms delay")
            handler.postDelayed({
                startPlayback(player)
            }, delayMs)
        } else {
            Log.d(TAG, "$trackName: Starting playback immediately")
            startPlayback(player)
        }
    }

    // Extracted playback logic
    private fun startPlayback(player: MediaPlayer) {
        try {
            if (pausedState) {
                player.start()
                pausedState = false
            } else {
                player.seekTo(0)
                savedPosition = 0
                player.start()
            }

            playingState = true
            startProgressTracking()
            Log.d(TAG, "$trackName: Playback started")
        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error starting playback", e)
            onError?.invoke("Error starting playback: ${e.message}")
        }
    }

    // UPDATED: Standard pause (uses pauseAtPosition)
    fun pause() {
        pauseAtPosition()
    }

    // UPDATED: Standard resume (uses resumeFromPosition)
    fun resume() {
        resumeFromPosition()
    }

    fun stop() {
        // Cancel any pending delayed playback
        handler.removeCallbacksAndMessages(null)

        val player = mediaPlayer ?: return

        try {
            if (playingState || pausedState) {
                player.stop()
                player.prepare()
            }

            playingState = false
            pausedState = false
            savedPosition = 0 // Reset position on stop
            stopProgressTracking()
            Log.d(TAG, "$trackName: Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error stopping playback", e)
        }
    }

    fun release() {
        stop()
        stopProgressTracking()
        mediaPlayer?.release()
        mediaPlayer = null
        audioUri = null
        Log.d(TAG, "$trackName: Resources released")
    }

    private fun startProgressTracking() {
        stopProgressTracking()

        progressRunnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer
                if (player != null && playingState && !pausedState) {
                    try {
                        val currentPosition = player.currentPosition
                        val duration = player.duration

                        handler.post {
                            onProgressUpdate?.invoke(currentPosition, duration)
                        }

                        handler.postDelayed(this, 100)
                    } catch (e: Exception) {
                        Log.w(TAG, "$trackName: Error getting playback position", e)
                    }
                }
            }
        }

        progressRunnable?.let { handler.post(it) }
    }

    private fun stopProgressTracking() {
        progressRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
        }
        progressRunnable = null
    }

    fun setOnProgressUpdateListener(listener: (Int, Int) -> Unit) {
        onProgressUpdate = listener
    }

    fun setOnPlaybackCompleteListener(listener: () -> Unit) {
        onPlaybackComplete = listener
    }

    fun setOnErrorListener(listener: (String) -> Unit) {
        onError = listener
    }

    fun isCurrentlyPlaying(): Boolean = playingState && !pausedState
    fun isCurrentlyPaused(): Boolean = pausedState

    // Get current delay
    fun getCurrentDelay(): Long = delayMs

    companion object {
        private const val TAG = "AudioTrackPlayer"
    }
}
