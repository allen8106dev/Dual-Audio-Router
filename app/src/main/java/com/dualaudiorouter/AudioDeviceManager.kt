package com.example.dualaudiorouter

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Manages audio device detection and routing
 * Handles device enumeration, selection, and change notifications
 */
class AudioDeviceManager(
    private val context: Context,
    private val onDevicesChanged: (List<AudioDevice>) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var currentDevices = mutableListOf<AudioDevice>()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            Log.d(TAG, "Audio devices added: ${addedDevices.size}")
            updateDeviceList()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            Log.d(TAG, "Audio devices removed: ${removedDevices.size}")
            updateDeviceList()
        }
    }

    init {
        // Register for device changes
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        updateDeviceList()
    }

    /**
     * Get all available output audio devices
     */
    fun getAvailableOutputDevices(): List<AudioDevice> {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val audioDevices = mutableListOf<AudioDevice>()

        devices.forEach { deviceInfo ->
            // Filter for common output device types
            when (deviceInfo.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    val deviceName = if (deviceInfo.productName.isNullOrEmpty()) {
                        AudioDevice.getDeviceTypeName(deviceInfo.type)
                    } else {
                        "${deviceInfo.productName} (${AudioDevice.getDeviceTypeName(deviceInfo.type)})"
                    }

                    audioDevices.add(
                        AudioDevice(
                            deviceInfo = deviceInfo,
                            name = deviceName,
                            type = deviceInfo.type
                        )
                    )
                }
            }
        }

        // Always add a default device option
        if (audioDevices.none { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }) {
            audioDevices.add(0, AudioDevice(
                deviceInfo = null,
                name = "System Default",
                type = -1
            ))
        }

        Log.d(TAG, "Found ${audioDevices.size} output devices")
        return audioDevices
    }

    /**
     * Update the device list and notify listeners
     */
    private fun updateDeviceList() {
        val devices = getAvailableOutputDevices()
        currentDevices.clear()
        currentDevices.addAll(devices)
        onDevicesChanged(devices)
    }

    /**
     * Get the current audio routing device
     */
    fun getCurrentAudioDevice(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // Priority order: Bluetooth A2DP > Wired > Speaker
        return devices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: devices.find { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
            ?: devices.find { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
            ?: devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    /**
     * Check if a specific device type is available
     */
    fun isDeviceTypeAvailable(deviceType: Int): Boolean {
        return currentDevices.any { it.type == deviceType }
    }

    /**
     * Get Bluetooth A2DP devices specifically
     */
    fun getBluetoothA2DPDevices(): List<AudioDevice> {
        return currentDevices.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
    }

    companion object {
        private const val TAG = "AudioDeviceManager"
    }
}
