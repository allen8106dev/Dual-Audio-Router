package com.example.dualaudiorouter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.content.Context
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {

    // UI elements
    private lateinit var btnSelectFileA: Button
    private lateinit var btnSelectFileB: Button
    private lateinit var tvFileNameA: TextView
    private lateinit var tvFileNameB: TextView
    private lateinit var spinnerDeviceA: Spinner
    private lateinit var spinnerDeviceB: Spinner
    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnStop: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    // Delay control elements
    private lateinit var seekBarDelayA: SeekBar
    private lateinit var seekBarDelayB: SeekBar
    private lateinit var tvDelayA: TextView
    private lateinit var tvDelayB: TextView
    private lateinit var btnResetDelays: Button

    // Volume control elements
    private lateinit var seekBarVolumeA: SeekBar
    private lateinit var seekBarVolumeB: SeekBar
    private lateinit var tvVolumeA: TextView
    private lateinit var tvVolumeB: TextView
    private lateinit var btnMuteA: Button
    private lateinit var btnMuteB: Button

    private lateinit var audioDeviceManager: AudioDeviceManager
    private lateinit var playerA: AudioTrackPlayer
    private lateinit var playerB: AudioTrackPlayer

    private var selectedFileA: Uri? = null
    private var selectedFileB: Uri? = null
    private var availableDevices = listOf<AudioDevice>()

    // Track playback states
    private var isTrackAPlaying = false
    private var isTrackBPlaying = false

    // Pause/resume state management
    private var isPausedState = false
    private var pausedAtTrackAPosition = 0

    // Service binding for background persistence
    private var audioService: AudioPlaybackService? = null
    private var isServiceBound = false

    // Preferences manager
    private lateinit var preferencesManager: PreferencesManager

    // Handler for delayed operations
    private val handler = Handler(Looper.getMainLooper())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlaybackService.AudioPlaybackBinder
            audioService = binder.getService()
            audioService?.setPlayers(playerA, playerB)
            isServiceBound = true
            Log.d(TAG, "Service connected and players set")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isServiceBound = false
            Log.d(TAG, "Service disconnected")
        }
    }

    // File picker launchers with persistent permission support
    private val filePickerA = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            Log.d(TAG, "File A selected via OpenDocument: $it")
            handleFileSelection(it, true)
        }
    }

    private val filePickerB = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            Log.d(TAG, "File B selected via OpenDocument: $it")
            handleFileSelection(it, false)
        }
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeAudioSystem()
        } else {
            showError("Required permissions not granted. App functionality will be limited.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        initializeViews()

        // Initialize preferences manager
        preferencesManager = PreferencesManager(this)

        // Initialize audio players
        playerA = AudioTrackPlayer(this, "Track A")
        playerB = AudioTrackPlayer(this, "Track B")

        // Bind to audio service for background persistence
        bindAudioService()

        setupUI()
        checkAndRequestPermissions()

        // Restore previous selections after initialization
        restorePreviousSelections()
    }

    // Restore previously selected files and settings
    private fun restorePreviousSelections() {
        Log.d(TAG, "🔄 Restoring previous selections...")

        // Check if we have any saved data
        if (!preferencesManager.hasSavedFiles()) {
            Log.d(TAG, "ℹ️ No saved files found")
            return
        }

        // Restore Track A
        val savedUriA = preferencesManager.getTrackAUri()
        val savedNameA = preferencesManager.getTrackAName()

        Log.d(TAG, "🎵 Attempting to restore Track A: URI=$savedUriA, Name=$savedNameA")

        if (savedUriA != null && savedNameA != null) {
            if (isUriValidAndAccessible(savedUriA)) {
                selectedFileA = savedUriA
                tvFileNameA.text = savedNameA
                Log.d(TAG, "✅ Successfully restored Track A: $savedNameA")
                updateStatus("Restored Track A: ${savedNameA.substringBefore("(").trim()}")
            } else {
                Log.w(TAG, "❌ Saved Track A URI is no longer valid or accessible")
                preferencesManager.clearTrackA()
                updateStatus("Previous Track A file no longer available")
            }
        }

        // Restore Track B
        val savedUriB = preferencesManager.getTrackBUri()
        val savedNameB = preferencesManager.getTrackBName()

        Log.d(TAG, "🎵 Attempting to restore Track B: URI=$savedUriB, Name=$savedNameB")

        if (savedUriB != null && savedNameB != null) {
            if (isUriValidAndAccessible(savedUriB)) {
                selectedFileB = savedUriB
                tvFileNameB.text = savedNameB
                Log.d(TAG, "✅ Successfully restored Track B: $savedNameB")
                updateStatus("Restored Track B: ${savedNameB.substringBefore("(").trim()}")
            } else {
                Log.w(TAG, "❌ Saved Track B URI is no longer valid or accessible")
                preferencesManager.clearTrackB()
                updateStatus("Previous Track B file no longer available")
            }
        }

        // Restore delay settings
        val savedDelayA = preferencesManager.getDelayA()
        val savedDelayB = preferencesManager.getDelayB()

        if (savedDelayA > 0 || savedDelayB > 0) {
            seekBarDelayA.progress = savedDelayA
            seekBarDelayB.progress = savedDelayB
            tvDelayA.text = "${savedDelayA}ms"
            tvDelayB.text = "${savedDelayB}ms"
            playerA.setDelay(savedDelayA.toLong())
            playerB.setDelay(savedDelayB.toLong())
            Log.d(TAG, "⏱️ Restored delays: A=${savedDelayA}ms, B=${savedDelayB}ms")
        }

        // Restore volume settings
        val savedVolumeA = preferencesManager.getVolumeA()
        val savedVolumeB = preferencesManager.getVolumeB()

        seekBarVolumeA.progress = savedVolumeA
        seekBarVolumeB.progress = savedVolumeB
        tvVolumeA.text = "${savedVolumeA}%"
        tvVolumeB.text = "${savedVolumeB}%"
        playerA.setVolumePercent(savedVolumeA)
        playerB.setVolumePercent(savedVolumeB)

        // Update mute button states
        btnMuteA.text = if (savedVolumeA == 0) "Unmute A" else "Mute A"
        btnMuteB.text = if (savedVolumeB == 0) "Unmute B" else "Mute B"

        Log.d(TAG, "⏱️ Restored volumes: A=${savedVolumeA}%, B=${savedVolumeB}%")

        // Show welcome message if any files were restored
        if (selectedFileA != null || selectedFileB != null) {
            updateStatus("🎉 Welcome back! Previous selections restored.")
        } else if (savedUriA != null || savedUriB != null) {
            updateStatus("⚠️ Some previous files are no longer available")
        }
    }

    // Enhanced URI validation
    private fun isUriValidAndAccessible(uri: Uri): Boolean {
        return try {
            // First check if we still have persistable permission
            val persistedUris = contentResolver.persistedUriPermissions
            val hasPermission = persistedUris.any { it.uri == uri }

            if (!hasPermission) {
                Log.w(TAG, "No persistable permission found for URI: $uri")
                return false
            }

            // Then try to actually access the file
            contentResolver.openInputStream(uri)?.use {
                Log.d(TAG, "✅ URI is valid and accessible: $uri")
                true
            } ?: false
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception accessing URI: $uri - ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "❌ URI validation failed: ${e.message}")
            false
        }
    }

    // Restore device selections after devices are loaded
    private fun restoreDeviceSelections() {
        val savedDeviceAIndex = preferencesManager.getDeviceAIndex()
        val savedDeviceBIndex = preferencesManager.getDeviceBIndex()

        if (savedDeviceAIndex >= 0 && savedDeviceAIndex < availableDevices.size) {
            spinnerDeviceA.setSelection(savedDeviceAIndex)
            Log.d(TAG, "Restored device A selection: index $savedDeviceAIndex")
        }

        if (savedDeviceBIndex >= 0 && savedDeviceBIndex < availableDevices.size) {
            spinnerDeviceB.setSelection(savedDeviceBIndex)
            Log.d(TAG, "Restored device B selection: index $savedDeviceBIndex")
        }
    }

    // Bind to audio service
    private fun bindAudioService() {
        val intent = Intent(this, AudioPlaybackService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "Attempting to bind to audio service")
    }

    // Start background service when playback begins
    private fun startBackgroundService() {
        val intent = Intent(this, AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_START_PLAYBACK
        }
        startForegroundService(intent)
        Log.d(TAG, "Started background service for persistence")
    }

    private fun initializeViews() {
        btnSelectFileA = findViewById(R.id.btnSelectFileA)
        btnSelectFileB = findViewById(R.id.btnSelectFileB)
        tvFileNameA = findViewById(R.id.tvFileNameA)
        tvFileNameB = findViewById(R.id.tvFileNameB)
        spinnerDeviceA = findViewById(R.id.spinnerDeviceA)
        spinnerDeviceB = findViewById(R.id.spinnerDeviceB)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        // Initialize delay controls
        seekBarDelayA = findViewById(R.id.seekBarDelayA)
        seekBarDelayB = findViewById(R.id.seekBarDelayB)
        tvDelayA = findViewById(R.id.tvDelayA)
        tvDelayB = findViewById(R.id.tvDelayB)
        btnResetDelays = findViewById(R.id.btnResetDelays)

        // Initialize volume controls
        seekBarVolumeA = findViewById(R.id.seekBarVolumeA)
        seekBarVolumeB = findViewById(R.id.seekBarVolumeB)
        tvVolumeA = findViewById(R.id.tvVolumeA)
        tvVolumeB = findViewById(R.id.tvVolumeB)
        btnMuteA = findViewById(R.id.btnMuteA)
        btnMuteB = findViewById(R.id.btnMuteB)
    }

    private fun setupUI() {
        btnSelectFileA.setOnClickListener {
            filePickerA.launch(arrayOf("audio/*"))
        }

        btnSelectFileB.setOnClickListener {
            filePickerB.launch(arrayOf("audio/*"))
        }

        btnPlay.setOnClickListener {
            playDualAudio()
        }

        btnPause.setOnClickListener {
            pausePlayback()
        }

        btnStop.setOnClickListener {
            stopPlayback()
        }

        // Setup delay controls with persistence
        seekBarDelayA.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playerA.setDelay(progress.toLong())
                    tvDelayA.text = "${progress}ms"
                    updateStatus("Track A delay: ${progress}ms")
                    preferencesManager.saveDelaySettings(progress, seekBarDelayB.progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarDelayB.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playerB.setDelay(progress.toLong())
                    tvDelayB.text = "${progress}ms"
                    updateStatus("Track B delay: ${progress}ms")
                    preferencesManager.saveDelaySettings(seekBarDelayA.progress, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup volume controls
        seekBarVolumeA.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playerA.setVolumePercent(progress)
                    tvVolumeA.text = "${progress}%"
                    updateStatus("Track A volume: ${progress}%")
                    preferencesManager.saveVolumeSettings(progress, seekBarVolumeB.progress)

                    // Update mute button state if volume changes
                    btnMuteA.text = if (progress == 0) "Unmute A" else "Mute A"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarVolumeB.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playerB.setVolumePercent(progress)
                    tvVolumeB.text = "${progress}%"
                    updateStatus("Track B volume: ${progress}%")
                    preferencesManager.saveVolumeSettings(seekBarVolumeA.progress, progress)

                    // Update mute button state if volume changes
                    btnMuteB.text = if (progress == 0) "Unmute B" else "Mute B"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Mute/unmute buttons
        btnMuteA.setOnClickListener {
            val currentVolume = seekBarVolumeA.progress
            if (currentVolume > 0) {
                // Mute Track A
                seekBarVolumeA.progress = 0
                tvVolumeA.text = "0%"
                playerA.setVolumePercent(0)
                btnMuteA.text = "Unmute A"
                updateStatus("Track A muted")
            } else {
                // Unmute Track A
                seekBarVolumeA.progress = 100
                tvVolumeA.text = "100%"
                playerA.setVolumePercent(100)
                btnMuteA.text = "Mute A"
                updateStatus("Track A unmuted")
            }
            // Save volume change
            preferencesManager.saveVolumeSettings(seekBarVolumeA.progress, seekBarVolumeB.progress)
        }

        btnMuteB.setOnClickListener {
            val currentVolume = seekBarVolumeB.progress
            if (currentVolume > 0) {
                // Mute Track B
                seekBarVolumeB.progress = 0
                tvVolumeB.text = "0%"
                playerB.setVolumePercent(0)
                btnMuteB.text = "Unmute B"
                updateStatus("Track B muted")
            } else {
                // Unmute Track B
                seekBarVolumeB.progress = 100
                tvVolumeB.text = "100%"
                playerB.setVolumePercent(100)
                btnMuteB.text = "Mute B"
                updateStatus("Track B unmuted")
            }
            // Save volume change
            preferencesManager.saveVolumeSettings(seekBarVolumeA.progress, seekBarVolumeB.progress)
        }

        btnResetDelays.setOnClickListener {
            seekBarDelayA.progress = 0
            seekBarDelayB.progress = 0
            playerA.setDelay(0)
            playerB.setDelay(0)
            tvDelayA.text = "0ms"
            tvDelayB.text = "0ms"
            updateStatus("Delays reset to 0ms")
            preferencesManager.saveDelaySettings(0, 0)
        }

        // Long press to clear all preferences
        btnResetDelays.setOnLongClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Clear All Preferences")
                .setMessage("This will clear all saved files, delays, volumes, and device selections. Continue?")
                .setPositiveButton("Clear All") { _, _ ->
                    preferencesManager.clearAll()

                    // Reset file selections
                    selectedFileA = null
                    selectedFileB = null
                    tvFileNameA.text = "No file selected"
                    tvFileNameB.text = "No file selected"

                    // Reset delays
                    seekBarDelayA.progress = 0
                    seekBarDelayB.progress = 0
                    tvDelayA.text = "0ms"
                    tvDelayB.text = "0ms"
                    playerA.setDelay(0)
                    playerB.setDelay(0)

                    // Reset volumes
                    seekBarVolumeA.progress = 100
                    seekBarVolumeB.progress = 100
                    tvVolumeA.text = "100%"
                    tvVolumeB.text = "100%"
                    playerA.setVolumePercent(100)
                    playerB.setVolumePercent(100)
                    btnMuteA.text = "Mute A"
                    btnMuteB.text = "Mute B"

                    updateStatus("All preferences cleared")
                    Toast.makeText(this, "All preferences cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        // Setup progress listeners
        playerA.setOnProgressUpdateListener { current, total ->
            val progress = if (total > 0) (current * 100 / total) else 0
            progressBar.progress = progress
        }

        playerA.setOnPlaybackCompleteListener {
            isTrackAPlaying = false
            updateStatus("Track A playback completed")
            audioService?.updatePlaybackState(false)
            if (!isTrackBPlaying) {
                resetPlaybackControls()
                audioService?.stopForegroundService()
            }
        }

        playerB.setOnPlaybackCompleteListener {
            isTrackBPlaying = false
            updateStatus("Track B playback completed")
            if (!isTrackAPlaying) {
                resetPlaybackControls()
                audioService?.stopForegroundService()
            }
        }

        playerA.setOnErrorListener { error ->
            showError("Player A Error: $error")
        }

        playerB.setOnErrorListener { error ->
            showError("Player B Error: $error")
        }

        updateStatus("Ready - Select audio files and adjust settings")
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
        }

        // Audio permissions
        if (checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        }

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initializeAudioSystem()
        }
    }

    // Initialize audio system with Bluetooth monitoring
    private fun initializeAudioSystem() {
        audioDeviceManager = AudioDeviceManager(
            context = this,
            onDevicesChanged = { devices ->
                availableDevices = devices
                updateDeviceSpinners(devices)
            },
            onBluetoothDisconnected = {
                handleBluetoothDisconnection()
            }
        )

        updateStatus("Audio system initialized. Found ${availableDevices.size} devices.")
    }

    // Handle Bluetooth disconnection
    private fun handleBluetoothDisconnection() {
        Log.d(TAG, "Handling Bluetooth disconnection")

        // Stop Track B only if it's playing
        if (isTrackBPlaying && playerB.isCurrentlyPlaying()) {
            playerB.stop()
            isTrackBPlaying = false

            audioService?.updatePlaybackState(isTrackAPlaying)
            Toast.makeText(this, "Bluetooth disconnected - Track B stopped", Toast.LENGTH_LONG).show()
            updateStatus("Bluetooth disconnected: Track B stopped, Track A continues")

            if (isTrackAPlaying && playerA.isCurrentlyPlaying()) {
                updateStatus("Track A continues playing after Bluetooth disconnect")
            } else {
                resetPlaybackControls()
                audioService?.stopForegroundService()
            }
        } else {
            updateStatus("Bluetooth disconnected (Track B was not playing)")
        }
    }

    // Update device spinners to restore selections
    private fun updateDeviceSpinners(devices: List<AudioDevice>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, devices)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerDeviceA.adapter = adapter
        spinnerDeviceB.adapter = adapter

        // Try to restore saved selections first
        restoreDeviceSelections()

        // If no saved selections, use defaults
        if (preferencesManager.getDeviceAIndex() < 0) {
            val speakerDevice = devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            speakerDevice?.let {
                val position = devices.indexOf(it)
                spinnerDeviceA.setSelection(position)
            }
        }

        if (preferencesManager.getDeviceBIndex() < 0) {
            val bluetoothDevice = devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            bluetoothDevice?.let {
                val position = devices.indexOf(it)
                spinnerDeviceB.setSelection(position)
            }
        }

        Log.d(TAG, "Device spinners updated with ${devices.size} devices")
    }

    // Handle file selection with persistence
    private fun handleFileSelection(uri: Uri, isTrackA: Boolean) {
        try {
            val fileName = getFileName(uri)
            Log.d(TAG, "Processing file selection: $fileName, URI: $uri")

            var fileSize = 0L
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(1024)
                    val bytesRead = stream.read(buffer)
                    if (bytesRead > 0) {
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                        Log.d(TAG, "File access successful: $fileName, size: $fileSize bytes")
                    } else {
                        throw Exception("File appears to be empty or unreadable")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot access selected file: $fileName", e)
                showError("Cannot access selected file. Please choose a different audio file.")
                return
            }

            // Take persistable permission immediately
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                Log.d(TAG, "✅ Persistable permission GRANTED for: $fileName")
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ Failed to take persistable permission for: $fileName - ${e.message}")
                showError("Cannot save file permission. File may not be remembered after restart.")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error taking persistable permission: ${e.message}")
            }

            val displayName = "$fileName (${formatFileSize(fileSize)})"

            if (isTrackA) {
                selectedFileA = uri
                tvFileNameA.text = displayName
                updateStatus("File A selected: $fileName")
                preferencesManager.saveTrackAUri(uri, displayName)
                Log.d(TAG, "💾 Saved Track A to preferences: $fileName")
            } else {
                selectedFileB = uri
                tvFileNameB.text = displayName
                updateStatus("File B selected: $fileName")
                preferencesManager.saveTrackBUri(uri, displayName)
                Log.d(TAG, "💾 Saved Track B to preferences: $fileName")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling file selection", e)
            showError("Error selecting file: ${e.message}")
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun getFileName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrEmpty()) name else "Audio file"
                } else {
                    "Audio file"
                }
            } ?: "Audio file"
        } catch (e: Exception) {
            Log.w(TAG, "Could not get file name from URI: $uri", e)
            "Audio file"
        }
    }

    // Play dual audio with resume support
    private fun playDualAudio() {
        // Check if this is a resume from pause
        if (isPausedState) {
            resumePlaybackWithDelay()
            return
        }

        // Original play logic for starting from beginning
        if (selectedFileA == null || selectedFileB == null) {
            showError("Please select both audio files first")
            return
        }

        if (availableDevices.isEmpty()) {
            showError("No audio devices available")
            return
        }

        try {
            val deviceA = spinnerDeviceA.selectedItem as AudioDevice
            val deviceB = spinnerDeviceB.selectedItem as AudioDevice

            updateStatus("Loading audio files...")

            val fileALoaded = playerA.loadAudioFile(selectedFileA!!)
            val fileBLoaded = playerB.loadAudioFile(selectedFileB!!)

            if (!fileALoaded || !fileBLoaded) {
                showError("Failed to load one or more audio files")
                return
            }

            updateStatus("Preparing audio tracks...")

            val trackAPrepared = playerA.prepareAudioTrack(deviceA)
            val trackBPrepared = playerB.prepareAudioTrack(deviceB)

            if (!trackAPrepared || !trackBPrepared) {
                showError("Failed to prepare audio tracks")
                return
            }

            val delayA = playerA.getCurrentDelay()
            val delayB = playerB.getCurrentDelay()
            updateStatus("Starting playback with delays: A=${delayA}ms, B=${delayB}ms")

            // Start both tracks from beginning
            playerA.play()
            playerB.play()

            isTrackAPlaying = true
            isTrackBPlaying = true
            isPausedState = false

            // Start background service
            startBackgroundService()
            audioService?.updateTrackInfo(
                getFileName(selectedFileA!!),
                getFileName(selectedFileB!!)
            )
            audioService?.updatePlaybackState(true)

            btnPlay.isEnabled = false
            btnPause.isEnabled = true
            btnStop.isEnabled = true

            updateStatus("Playback started - audio will continue in background")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting dual audio playback", e)
            showError("Error starting playback: ${e.message}")
        }
    }

    // Resume with proper delay synchronization
    private fun resumePlaybackWithDelay() {
        try {
            // Get current manual delay setting
            val manualDelayB = seekBarDelayB.progress.toLong()

            Log.d(TAG, "Resuming from Track A position: ${pausedAtTrackAPosition}ms with Track B delay: ${manualDelayB}ms")

            // Calculate positions for resume
            val trackAResumePosition = pausedAtTrackAPosition
            val trackBResumePosition = pausedAtTrackAPosition + manualDelayB.toInt()

            updateStatus("Resuming with synchronization...")

            // Resume Track A at its paused position
            playerA.resumeFromPosition(trackAResumePosition)

            // Resume Track B at Track A position + manual delay
            playerB.resumeFromPosition(trackBResumePosition)

            // Update states
            isPausedState = false

            // Update service and UI
            audioService?.updatePlaybackState(true)

            btnPlay.isEnabled = false
            btnPause.isEnabled = true
            btnStop.isEnabled = true

            updateStatus("Resumed: Track A at ${formatTime(trackAResumePosition)}, Track B at ${formatTime(trackBResumePosition)}")

            Log.d(TAG, "Successfully resumed with Track A at ${trackAResumePosition}ms, Track B at ${trackBResumePosition}ms")

        } catch (e: Exception) {
            Log.e(TAG, "Error resuming with delay", e)
            showError("Error resuming playback: ${e.message}")
        }
    }

    // Pause playback with synchronization
    private fun pausePlayback() {
        if (!isTrackAPlaying && !isTrackBPlaying) {
            Log.d(TAG, "No tracks playing, ignoring pause")
            return
        }

        try {
            // Step 1: Get current Track A position
            pausedAtTrackAPosition = playerA.getCurrentPosition()
            Log.d(TAG, "Pausing at Track A position: ${pausedAtTrackAPosition}ms")

            // Step 2: Pause both tracks AT Track A's position (synchronizes them)
            playerA.pauseAtPosition(pausedAtTrackAPosition)
            playerB.pauseAtPosition(pausedAtTrackAPosition) // Both at same position

            // Step 3: Update states
            isPausedState = true

            // Step 4: Update UI and service
            updateStatus("Paused at ${formatTime(pausedAtTrackAPosition)} - both tracks synchronized")
            audioService?.updatePlaybackState(false)

            btnPlay.isEnabled = true
            btnPause.isEnabled = false

            Log.d(TAG, "Successfully paused both tracks at position: ${pausedAtTrackAPosition}ms")

        } catch (e: Exception) {
            Log.e(TAG, "Error during pause", e)
            showError("Error pausing playback: ${e.message}")
        }
    }

    // Stop playback and reset pause state
    private fun stopPlayback() {
        playerA.stop()
        playerB.stop()

        isTrackAPlaying = false
        isTrackBPlaying = false
        isPausedState = false  // Reset pause state
        pausedAtTrackAPosition = 0  // Reset saved position

        // Stop background service
        audioService?.stopForegroundService()

        resetPlaybackControls()
        updateStatus("Playback stopped and reset")
    }

    private fun resetPlaybackControls() {
        btnPlay.isEnabled = true
        btnPause.isEnabled = false
        btnStop.isEnabled = false
        progressBar.progress = 0
    }

    // Helper method for time formatting
    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    private fun updateStatus(status: String) {
        tvStatus.text = status
        Log.d(TAG, "Status: $status")
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        updateStatus("Error: $message")
        Log.e(TAG, "Error: $message")
    }

    // Save device selections when they change
    override fun onPause() {
        super.onPause()

        // Save current device selections
        if (::audioDeviceManager.isInitialized && availableDevices.isNotEmpty()) {
            val deviceAIndex = spinnerDeviceA.selectedItemPosition
            val deviceBIndex = spinnerDeviceB.selectedItemPosition
            preferencesManager.saveDeviceSelections(deviceAIndex, deviceBIndex)
        }
    }

    // Handle app coming back to foreground
    override fun onResume() {
        super.onResume()

        // Reconnect to service if needed
        if (!isServiceBound) {
            bindAudioService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unbind from service
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding service: ${e.message}")
            }
            isServiceBound = false
        }

        playerA.release()
        playerB.release()

        if (::audioDeviceManager.isInitialized) {
            audioDeviceManager.cleanup()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
