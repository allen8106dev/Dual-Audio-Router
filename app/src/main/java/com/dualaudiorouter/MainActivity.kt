package com.example.dualaudiorouter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // UI elements - no binding
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

        // Initialize UI elements with findViewById
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
    }

    /**
     * Setup UI event handlers
     */
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

        updateStatus("Ready - Please select audio files and check permissions")
    }

    /**
     * Check and request necessary permissions
     */
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

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initializeAudioSystem()
        }
    }

    /**
     * Initialize audio device management
     */
    private fun initializeAudioSystem() {
        audioDeviceManager = AudioDeviceManager(this) { devices ->
            availableDevices = devices
            updateDeviceSpinners(devices)
        }

        updateStatus("Audio system initialized. Found ${availableDevices.size} devices.")
    }

    /**
     * Update device selection spinners
     */
    private fun updateDeviceSpinners(devices: List<AudioDevice>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, devices)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerDeviceA.adapter = adapter
        spinnerDeviceB.adapter = adapter

        // Auto-select preferred devices
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

    /**
     * Handle file selection with proper permission handling
     */
    private fun handleFileSelection(uri: Uri, isTrackA: Boolean) {
        try {
            val fileName = getFileName(uri)

            // Try to take persistent permission, but don't fail if it's not available
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d(TAG, "Persistent permission granted for: $fileName")
            } catch (e: SecurityException) {
                // This is okay - some URIs don't support persistent permissions
                // The URI will still work for immediate access
                Log.w(TAG, "Persistent permission not available for: $fileName - ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not take persistent permission for: $fileName - ${e.message}")
            }

            // Test if we can actually read the file
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val testBytes = ByteArray(1024)
                    stream.read(testBytes)
                    Log.d(TAG, "File access test successful for: $fileName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot read selected file: $fileName", e)
                showError("Cannot read selected file. Please choose a different file.")
                return
            }

            if (isTrackA) {
                selectedFileA = uri
                tvFileNameA.text = fileName
                updateStatus("File A selected: $fileName")
            } else {
                selectedFileB = uri
                tvFileNameB.text = fileName
                updateStatus("File B selected: $fileName")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling file selection", e)
            showError("Error selecting file: ${e.message}")
        }
    }

    /**
     * Get file name from URI
     */
    private fun getFileName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "Unknown file"
        } catch (e: Exception) {
            "Audio file"
        }
    }

    /**
     * Play both audio tracks simultaneously
     */
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
            // Get selected devices
            val deviceA = spinnerDeviceA.selectedItem as AudioDevice
            val deviceB = spinnerDeviceB.selectedItem as AudioDevice

            updateStatus("Loading audio files...")

            // Load audio files
            val fileALoaded = playerA.loadAudioFile(selectedFileA!!)
            val fileBLoaded = playerB.loadAudioFile(selectedFileB!!)

            if (!fileALoaded || !fileBLoaded) {
                showError("Failed to load one or more audio files")
                return
            }

            updateStatus("Preparing audio tracks...")

            // Prepare audio tracks with device routing
            val trackAPrepared = playerA.prepareAudioTrack(deviceA)
            val trackBPrepared = playerB.prepareAudioTrack(deviceB)

            if (!trackAPrepared || !trackBPrepared) {
                showError("Failed to prepare audio tracks")
                return
            }

            updateStatus("Starting synchronized playback...")

            // Start both tracks as close to simultaneously as possible
            playerA.play()
            playerB.play()

            updateStatus("Playing: Track A -> ${deviceA.name}, Track B -> ${deviceB.name}")

            // Update UI
            btnPlay.isEnabled = false
            btnPause.isEnabled = true
            btnStop.isEnabled = true

        } catch (e: Exception) {
            Log.e(TAG, "Error starting dual audio playback", e)
            showError("Error starting playback: ${e.message}")
        }
    }

    /**
     * Pause both audio tracks
     */
    private fun pausePlayback() {
        playerA.pause()
        playerB.pause()

        updateStatus("Playback paused")

        btnPlay.isEnabled = true
        btnPause.isEnabled = false
    }

    /**
     * Stop both audio tracks
     */
    private fun stopPlayback() {
        playerA.stop()
        playerB.stop()

        resetPlaybackControls()
        updateStatus("Playback stopped")
    }

    /**
     * Reset playback control buttons
     */
    private fun resetPlaybackControls() {
        btnPlay.isEnabled = true
        btnPause.isEnabled = false
        btnStop.isEnabled = false
        progressBar.progress = 0
    }

    /**
     * Update status display
     */
    private fun updateStatus(status: String) {
        tvStatus.text = status
        Log.d(TAG, "Status: $status")
    }

    /**
     * Show error message
     */
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        updateStatus("Error: $message")
        Log.e(TAG, "Error: $message")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up resources
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
