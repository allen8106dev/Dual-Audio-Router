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

    // NEW: Delay control elements
    private lateinit var seekBarDelayA: SeekBar
    private lateinit var seekBarDelayB: SeekBar
    private lateinit var tvDelayA: TextView
    private lateinit var tvDelayB: TextView
    private lateinit var btnResetDelays: Button

    private lateinit var audioDeviceManager: AudioDeviceManager
    private lateinit var playerA: AudioTrackPlayer
    private lateinit var playerB: AudioTrackPlayer

    private var selectedFileA: Uri? = null
    private var selectedFileB: Uri? = null
    private var availableDevices = listOf<AudioDevice>()

    // File picker launchers
    private val filePickerA = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleFileSelection(it, true) }
    }

    private val filePickerB = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleFileSelection(it, false) }
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

        // Initialize audio players
        playerA = AudioTrackPlayer(this, "Track A")
        playerB = AudioTrackPlayer(this, "Track B")

        setupUI()
        checkAndRequestPermissions()
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

        // NEW: Initialize delay controls
        seekBarDelayA = findViewById(R.id.seekBarDelayA)
        seekBarDelayB = findViewById(R.id.seekBarDelayB)
        tvDelayA = findViewById(R.id.tvDelayA)
        tvDelayB = findViewById(R.id.tvDelayB)
        btnResetDelays = findViewById(R.id.btnResetDelays)
    }

    private fun setupUI() {
        btnSelectFileA.setOnClickListener {
            filePickerA.launch("audio/*")
        }

        btnSelectFileB.setOnClickListener {
            filePickerB.launch("audio/*")
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

        // NEW: Setup delay controls
        seekBarDelayA.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playerA.setDelay(progress.toLong())
                    tvDelayA.text = "${progress}ms"
                    updateStatus("Track A delay: ${progress}ms")
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
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnResetDelays.setOnClickListener {
            seekBarDelayA.progress = 0
            seekBarDelayB.progress = 0
            playerA.setDelay(0)
            playerB.setDelay(0)
            tvDelayA.text = "0ms"
            tvDelayB.text = "0ms"
            updateStatus("Delays reset to 0ms")
        }

        // Setup progress listeners
        playerA.setOnProgressUpdateListener { current, total ->
            val progress = if (total > 0) (current * 100 / total) else 0
            progressBar.progress = progress
        }

        playerA.setOnPlaybackCompleteListener {
            updateStatus("Playback completed")
            resetPlaybackControls()
        }

        playerA.setOnErrorListener { error ->
            showError("Player A Error: $error")
        }

        playerB.setOnErrorListener { error ->
            showError("Player B Error: $error")
        }

        updateStatus("Ready - Select audio files and adjust sync delays if needed")
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
        }

        if (checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initializeAudioSystem()
        }
    }

    private fun initializeAudioSystem() {
        audioDeviceManager = AudioDeviceManager(this) { devices ->
            availableDevices = devices
            updateDeviceSpinners(devices)
        }

        updateStatus("Audio system initialized. Found ${availableDevices.size} devices.")
    }

    private fun updateDeviceSpinners(devices: List<AudioDevice>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, devices)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerDeviceA.adapter = adapter
        spinnerDeviceB.adapter = adapter

        val speakerDevice = devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val bluetoothDevice = devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }

        speakerDevice?.let {
            val position = devices.indexOf(it)
            spinnerDeviceA.setSelection(position)
        }

        bluetoothDevice?.let {
            val position = devices.indexOf(it)
            spinnerDeviceB.setSelection(position)
        }

        Log.d(TAG, "Device spinners updated with ${devices.size} devices")
    }

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
                            cursor.moveToFirst()
                            fileSize = cursor.getLong(sizeIndex)
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

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d(TAG, "Persistent permission granted for: $fileName")
            } catch (e: SecurityException) {
                Log.w(TAG, "Persistent permission not supported for: $fileName")
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "Persistent permission not available for: $fileName")
            } catch (e: Exception) {
                Log.w(TAG, "Could not take persistent permission: ${e.message}")
            }

            if (isTrackA) {
                selectedFileA = uri
                tvFileNameA.text = "$fileName (${formatFileSize(fileSize)})"
                updateStatus("File A selected: $fileName")
            } else {
                selectedFileB = uri
                tvFileNameB.text = "$fileName (${formatFileSize(fileSize)})"
                updateStatus("File B selected: $fileName")
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

    private fun playDualAudio() {
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

            // NEW: Show delay info
            val delayA = playerA.getCurrentDelay()
            val delayB = playerB.getCurrentDelay()
            updateStatus("Starting playback with delays: A=${delayA}ms, B=${delayB}ms")

            // Start both tracks (delays will be applied automatically)
            playerA.play()
            playerB.play()

            btnPlay.isEnabled = false
            btnPause.isEnabled = true
            btnStop.isEnabled = true

        } catch (e: Exception) {
            Log.e(TAG, "Error starting dual audio playback", e)
            showError("Error starting playback: ${e.message}")
        }
    }

    private fun pausePlayback() {
        playerA.pause()
        playerB.pause()

        updateStatus("Playback paused")

        btnPlay.isEnabled = true
        btnPause.isEnabled = false
    }

    private fun stopPlayback() {
        playerA.stop()
        playerB.stop()

        resetPlaybackControls()
        updateStatus("Playback stopped")
    }

    private fun resetPlaybackControls() {
        btnPlay.isEnabled = true
        btnPause.isEnabled = false
        btnStop.isEnabled = false
        progressBar.progress = 0
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

    override fun onDestroy() {
        super.onDestroy()

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
