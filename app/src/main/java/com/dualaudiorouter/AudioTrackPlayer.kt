package com.example.dualaudiorouter

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.FileInputStream
import java.io.IOException

/**
 * Custom AudioTrack player with device routing support
 * Handles individual audio track playback with specified device routing
 */
class AudioTrackPlayer(
    private val context: Context,
    private val trackName: String
) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var isPaused = false
    private var audioData: ByteArray? = null
    private var currentPosition = 0
    private val handler = Handler(Looper.getMainLooper())

    private var onProgressUpdate: ((Int, Int) -> Unit)? = null
    private var onPlaybackComplete: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    /**
     * Load audio file from URI
     */
    fun loadAudioFile(uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            audioData = inputStream?.readBytes()
            inputStream?.close()

            if (audioData != null) {
                Log.d(TAG, "$trackName: Audio file loaded, size: ${audioData!!.size} bytes")
                true
            } else {
                onError?.invoke("Failed to read audio data")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error loading audio file", e)
            onError?.invoke("Error loading file: ${e.message}")
            false
        }
    }

    /**
     * Prepare AudioTrack with specified device
     */
    fun prepareAudioTrack(targetDevice: AudioDevice?): Boolean {
        try {
            val audioData = this.audioData ?: return false

            // Create AudioTrack with proper configuration
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(44100) // Standard sample rate
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()

            val bufferSize = AudioTrack.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize * 2) // Double buffer for smooth playback
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // Set preferred device if API 23+ and device is available
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                targetDevice?.deviceInfo?.let { deviceInfo ->
                    val success = audioTrack?.setPreferredDevice(deviceInfo) == true
                    Log.d(TAG, "$trackName: Set preferred device ${deviceInfo.productName}: $success")

                    if (!success) {
                        Log.w(TAG, "$trackName: Failed to set preferred device, using default")
                    }
                }
            }

            return audioTrack?.state == AudioTrack.STATE_INITIALIZED

        } catch (e: Exception) {
            Log.e(TAG, "$trackName: Error preparing AudioTrack", e)
            onError?.invoke("Error preparing audio: ${e.message}")
            return false
        }
    }

    /**
     * Start playback
     */
    fun play() {
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            onError?.invoke("AudioTrack not properly initialized")
            return
        }

        if (isPlaying) return

        isPlaying = true
        isPaused = false

        audioTrack?.play()

        // Start playback in background thread
        Thread {
            playAudioData()
        }.start()

        Log.d(TAG, "$trackName: Playback started")
    }

    /**
     * Pause playback
     */
    fun pause() {
        if (!isPlaying || isPaused) return

        isPaused = true
        audioTrack?.pause()
        Log.d(TAG, "$trackName: Playback paused")
    }

    /**
     * Resume playback
     */
    fun resume() {
        if (!isPlaying || !isPaused) return

        isPaused = false
        audioTrack?.play()

        // Continue playback
        Thread {
            playAudioData()
        }.start()

        Log.d(TAG, "$trackName: Playback resumed")
    }

    /**
     * Stop playback
     */
    fun stop() {
        if (!isPlaying) return

        isPlaying = false
        isPaused = false
        currentPosition = 0

        audioTrack?.stop()
        audioTrack?.flush()

        Log.d(TAG, "$trackName: Playback stopped")
    }

    /**
     * Release resources
     */
    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
        audioData = null
        Log.d(TAG, "$trackName: Resources released")
    }

    /**
     * Play audio data in chunks
     */
    private fun playAudioData() {
        val data = audioData ?: return
        val chunkSize = 4096

        while (isPlaying && currentPosition < data.size) {
            if (isPaused) {
                Thread.sleep(100)
                continue
            }

            val remainingBytes = data.size - currentPosition
            val bytesToWrite = minOf(chunkSize, remainingBytes)

            val bytesWritten = audioTrack?.write(
                data,
                currentPosition,
                bytesToWrite
            ) ?: 0

            if (bytesWritten > 0) {
                currentPosition += bytesWritten

                // Update progress on main thread
                handler.post {
                    onProgressUpdate?.invoke(currentPosition, data.size)
                }
            } else {
                break
            }

            // Small delay to prevent overwhelming the audio system
            Thread.sleep(10)
        }

        // Playback complete
        if (currentPosition >= data.size) {
            handler.post {
                isPlaying = false
                onPlaybackComplete?.invoke()
            }
        }
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
     * Get current playback state
     */
    fun isPlaying(): Boolean = isPlaying && !isPaused
    fun isPaused(): Boolean = isPaused

    companion object {
        private const val TAG = "AudioTrackPlayer"
    }
}
