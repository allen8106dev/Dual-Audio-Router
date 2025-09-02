package com.example.dualaudiorouter

import android.media.AudioDeviceInfo

/**
 * Data class to represent audio device information
 */
data class AudioDevice(
    val deviceInfo: AudioDeviceInfo?,
    val name: String,
    val type: Int,
    val isAvailable: Boolean = true
) {
    override fun toString(): String = name

    companion object {
        fun getDeviceTypeName(type: Int): String {
            return when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
                else -> "Unknown Device"
            }
        }
    }
}
