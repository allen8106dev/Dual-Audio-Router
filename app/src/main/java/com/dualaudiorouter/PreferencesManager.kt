package com.example.dualaudiorouter

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log

/**
 * Manages app preferences and persistent file selections
 */
class PreferencesManager(context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    /**
     * Save selected Track A file URI
     */
    fun saveTrackAUri(uri: Uri, fileName: String) {
        preferences.edit().apply {
            putString(KEY_TRACK_A_URI, uri.toString())
            putString(KEY_TRACK_A_NAME, fileName)
            apply()
        }
        Log.d(TAG, "Saved Track A: $fileName")
    }

    /**
     * Save selected Track B file URI
     */
    fun saveTrackBUri(uri: Uri, fileName: String) {
        preferences.edit().apply {
            putString(KEY_TRACK_B_URI, uri.toString())
            putString(KEY_TRACK_B_NAME, fileName)
            apply()
        }
        Log.d(TAG, "Saved Track B: $fileName")
    }

    /**
     * Get saved Track A URI
     */
    fun getTrackAUri(): Uri? {
        val uriString = preferences.getString(KEY_TRACK_A_URI, null)
        return if (uriString != null) {
            try {
                Uri.parse(uriString)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved Track A URI: $e")
                null
            }
        } else null
    }

    /**
     * Get saved Track B URI
     */
    fun getTrackBUri(): Uri? {
        val uriString = preferences.getString(KEY_TRACK_B_URI, null)
        return if (uriString != null) {
            try {
                Uri.parse(uriString)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved Track B URI: $e")
                null
            }
        } else null
    }

    /**
     * Get saved Track A file name
     */
    fun getTrackAName(): String? {
        return preferences.getString(KEY_TRACK_A_NAME, null)
    }

    /**
     * Get saved Track B file name
     */
    fun getTrackBName(): String? {
        return preferences.getString(KEY_TRACK_B_NAME, null)
    }

    /**
     * Save device selection preferences
     */
    fun saveDeviceSelections(deviceAIndex: Int, deviceBIndex: Int) {
        preferences.edit().apply {
            putInt(KEY_DEVICE_A_INDEX, deviceAIndex)
            putInt(KEY_DEVICE_B_INDEX, deviceBIndex)
            apply()
        }
        Log.d(TAG, "Saved device selections: A=$deviceAIndex, B=$deviceBIndex")
    }

    /**
     * Get saved device A selection
     */
    fun getDeviceAIndex(): Int {
        return preferences.getInt(KEY_DEVICE_A_INDEX, -1)
    }

    /**
     * Get saved device B selection
     */
    fun getDeviceBIndex(): Int {
        return preferences.getInt(KEY_DEVICE_B_INDEX, -1)
    }

    /**
     * Save delay settings
     */
    fun saveDelaySettings(delayA: Int, delayB: Int) {
        preferences.edit().apply {
            putInt(KEY_DELAY_A, delayA)
            putInt(KEY_DELAY_B, delayB)
            apply()
        }
        Log.d(TAG, "Saved delay settings: A=${delayA}ms, B=${delayB}ms")
    }

    /**
     * Get saved delay A
     */
    fun getDelayA(): Int {
        return preferences.getInt(KEY_DELAY_A, 0)
    }

    /**
     * Get saved delay B
     */
    fun getDelayB(): Int {
        return preferences.getInt(KEY_DELAY_B, 0)
    }

    /**
     * Clear all saved preferences
     */
    fun clearAll() {
        preferences.edit().clear().apply()
        Log.d(TAG, "Cleared all preferences")
    }

    /**
     * Check if we have any saved files
     */
    fun hasSavedFiles(): Boolean {
        return preferences.contains(KEY_TRACK_A_URI) || preferences.contains(KEY_TRACK_B_URI)
    }

    // Add these methods to PreferencesManager class

    fun clearTrackA() {
        preferences.edit().apply {
            remove(KEY_TRACK_A_URI)
            remove(KEY_TRACK_A_NAME)
            apply()
        }
        Log.d(TAG, "🗑️ Cleared Track A preferences")
    }

    fun clearTrackB() {
        preferences.edit().apply {
            remove(KEY_TRACK_B_URI)
            remove(KEY_TRACK_B_NAME)
            apply()
        }
        Log.d(TAG, "🗑️ Cleared Track B preferences")
    }

    // NEW: Save volume settings
    fun saveVolumeSettings(volumeA: Int, volumeB: Int) {
        preferences.edit().apply {
            putInt(KEY_VOLUME_A, volumeA)
            putInt(KEY_VOLUME_B, volumeB)
            apply()
        }
        Log.d(TAG, "Saved volume settings: A=${volumeA}%, B=${volumeB}%")
    }

    // NEW: Get saved volume A
    fun getVolumeA(): Int {
        return preferences.getInt(KEY_VOLUME_A, 100) // Default to 100%
    }

    // NEW: Get saved volume B
    fun getVolumeB(): Int {
        return preferences.getInt(KEY_VOLUME_B, 100) // Default to 100%
    }
    companion object {
        private const val TAG = "PreferencesManager"
        private const val PREFS_NAME = "dual_audio_router_prefs"

        private const val KEY_TRACK_A_URI = "track_a_uri"
        private const val KEY_TRACK_A_NAME = "track_a_name"
        private const val KEY_TRACK_B_URI = "track_b_uri"
        private const val KEY_TRACK_B_NAME = "track_b_name"

        private const val KEY_DEVICE_A_INDEX = "device_a_index"
        private const val KEY_DEVICE_B_INDEX = "device_b_index"

        private const val KEY_DELAY_A = "delay_a"
        private const val KEY_DELAY_B = "delay_b"

        // NEW: Volume keys
        private const val KEY_VOLUME_A = "volume_a"
        private const val KEY_VOLUME_B = "volume_b"
    }
}
