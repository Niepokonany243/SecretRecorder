package com.example.systemservice

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Build
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.util.concurrent.Executors
import android.app.ActivityManager
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.appcompat.app.AppCompatDelegate

class MainActivity : SecretRecorderBaseActivity() {

    companion object {
        private const val TAG = "MainActivity"

        // Video quality presets
        private val QUALITY_OPTIONS = mapOf(
            "High (1080p)" to VideoQuality(1920, 1080, 8_000_000),
            "Medium (720p)" to VideoQuality(1280, 720, 5_000_000),
            "Low (480p)" to VideoQuality(854, 480, 2_500_000)
        )
    }

    // Service connection
    private var serviceBound = false
    private var secretRecorderService: SecretRecorderService.LocalBinder? = null
    private var serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            serviceBound = true
            secretRecorderService = service as SecretRecorderService.LocalBinder
            isServiceRunning = true
            updateRecordingUI(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            serviceBound = false
            secretRecorderService = null
            isServiceRunning = false
            updateRecordingUI(false)
        }
    }

    // Add settingsManager as a class member
    private lateinit var settingsManager: AppSettingsManager

    // Privacy indicator manager
    private lateinit var privacyIndicatorManager: PrivacyIndicatorManager

    // UI elements
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var cameraPreview: TextureView
    private lateinit var recordingStatus: TextView
    private lateinit var recordingIndicator: ImageView
    private lateinit var recordingTimer: Chronometer
    private lateinit var recordingModeToggle: MaterialButtonToggleGroup
    private lateinit var startButton: FloatingActionButton
    private lateinit var switchCameraButton: ImageButton
    private lateinit var viewRecordingsButton: Button
    private lateinit var flashButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var scheduleButton: MaterialButton

    // Camera variables
    private var useFrontCamera = true
    private var isAudioOnly = false
    private var useFlash = false
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var previewSurface: Surface? = null
    private var captureSession: CameraCaptureSession? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    // Recording settings
    private var selectedQuality = "Medium (720p)"

    // Service status
    private var isServiceRunning = false

    // Camera preview callback
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "Surface texture available: $width x $height")

            try {
                // Initialize the surface texture with the correct size
                surface.setDefaultBufferSize(width, height)

                // Release any existing surface
                previewSurface?.release()

                // Create the Surface object here to avoid the lateinit error
                previewSurface = Surface(surface)

                // Now setup the camera
                setupCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onSurfaceTextureAvailable: ${e.message}")
                // Try to recover
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (cameraPreview.isAvailable) {
                            previewSurface = Surface(cameraPreview.surfaceTexture)
                            setupCamera()
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error in recovery attempt: ${e2.message}")
                    }
                }, 500)
            }
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "Surface texture size changed: $width x $height")
            // Update buffer size when the surface changes
            surface.setDefaultBufferSize(width, height)

            // Recreate the surface with the new size
            previewSurface?.release()
            previewSurface = Surface(surface)

            // Restart the camera preview with the new size
            if (cameraDevice != null) {
                closeCamera()
                setupCamera()
            }
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.d(TAG, "Surface texture destroyed")
            // Clean up resources
            previewSurface?.release()
            previewSurface = null
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            // Not needed for this implementation
        }
    }

    // Camera state callback
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
            Toast.makeText(this@MainActivity, "Camera error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize settings manager
        settingsManager = AppSettingsManager(this)

        // Initialize privacy indicator manager
        privacyIndicatorManager = PrivacyIndicatorManager(this)

        // Initialize UI elements
        initializeViews()

        // Setup permission launcher
        initializePermissionLauncher()

        // Setup listeners
        setupListeners()

        // Check if service is running and bind to it
        if (isServiceRunning()) {
            bindToService()
        }

        // Request permissions
        checkAndRequestPermissions()
    }

    override fun onStart() {
        super.onStart()
        // Bind to the service if it's running
        if (isServiceRunning() && !serviceBound) {
            bindToService()
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from service but don't stop it
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun initializeViews() {
        // Find all views by ID
        cameraPreview = findViewById(R.id.cameraPreview)
        recordingStatus = findViewById(R.id.recordingStatus)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        recordingTimer = findViewById(R.id.recordingTimer)
        startButton = findViewById(R.id.startButton)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        viewRecordingsButton = findViewById(R.id.viewRecordingsButton)
        flashButton = findViewById(R.id.flashButton)
        recordingModeToggle = findViewById(R.id.recordingModeToggle)
        settingsButton = findViewById(R.id.settingsButton)
        scheduleButton = findViewById(R.id.scheduleButton)

        // Initialize camera manager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Initialize recording mode - get from settings
        isAudioOnly = settingsManager.isAudioOnly()
        recordingModeToggle.check(if (isAudioOnly) R.id.audioModeButton else R.id.videoModeButton)
        updateUiForRecordingMode()
    }

    private fun initializePermissionLauncher() {
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Log the permission results
            Log.d(TAG, "Permission results:")
            permissions.forEach { (permission, isGranted) ->
                Log.d(TAG, "$permission: ${if (isGranted) "GRANTED" else "DENIED"}")
            }

            val allGranted = permissions.entries.all { it.value }

            if (allGranted) {
                Log.d(TAG, "All requested permissions were granted")
                // Start background thread before setting up camera
                startBackgroundThread()
                setupCamera()
            } else {
                // Check which permissions were denied
                val deniedPermissions = permissions.filter { !it.value }.keys.toList()
                Log.d(TAG, "Some permissions were denied: $deniedPermissions")

                // Check if camera or microphone permissions were denied
                val cameraOrMicDenied = deniedPermissions.any {
                    it == Manifest.permission.CAMERA || it == Manifest.permission.RECORD_AUDIO
                }

                if (cameraOrMicDenied) {
                    // Only show toast if camera or mic permissions were denied
                    Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_SHORT).show()

                    // On Android 14+, provide more specific guidance
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        Log.d(TAG, "Showing Android 14+ specific permission guidance")
                        // Consider showing a dialog with more detailed instructions
                        // or directing users to app settings
                    }
                } else {
                    // If only non-essential permissions were denied, still proceed
                    Log.d(TAG, "Only non-essential permissions were denied, proceeding with setup")
                    startBackgroundThread()
                    setupCamera()
                }
            }
        }
    }

    private fun setupListeners() {
        // Start/stop recording button
        startButton.setOnClickListener {
            if (isServiceRunning) {
                stopRecordingService()
            } else {
                startCameraRecording()
            }
        }

        // Switch camera button
        switchCameraButton.setOnClickListener {
            useFrontCamera = !useFrontCamera
            closeCamera()
            setupCamera()
        }

        // View recordings button
        viewRecordingsButton.setOnClickListener {
            openRecordingsBrowser()
        }

        // Flash toggle button
        flashButton.setOnClickListener {
            toggleFlash()
        }

        // Recording mode toggle
        recordingModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isAudioOnly = (checkedId == R.id.audioModeButton)
                settingsManager.setAudioOnly(isAudioOnly) // Save to settings
                updateUiForRecordingMode()
            }
        }

        // Settings button
        settingsButton.setOnClickListener {
            openSettings()
        }

        // Schedule button
        scheduleButton.setOnClickListener {
            showScheduleDialog()
        }
    }

    private fun updateUiForRecordingMode() {
        if (isAudioOnly) {
            cameraPreview.visibility = View.GONE
            flashButton.visibility = View.GONE
            switchCameraButton.isEnabled = false
        } else {
            cameraPreview.visibility = View.VISIBLE
            flashButton.visibility = View.VISIBLE
            switchCameraButton.isEnabled = true
        }
    }

    override fun onResume() {
        super.onResume()

        // Log current settings
        Log.d(TAG, "Current settings on activity resume:")
        settingsManager.logAllSettings()

        // Check if service is running using a more reliable method
        isServiceRunning = isServiceRunning()

        // Only start background thread and setup camera if we're not recording
        if (!isServiceRunning) {
            startBackgroundThread()

            if (cameraPreview.isAvailable) {
                setupCamera()
            } else {
                cameraPreview.surfaceTextureListener = surfaceTextureListener
            }
        } else {
            // If recording is active, just bind to the service to get updates
            if (!serviceBound) {
                bindToService()
            }
        }

        updateRecordingUI(isServiceRunning)

        // Apply recording mode UI
        updateUiForRecordingMode()
    }

    override fun onPause() {
        // Only stop camera preview if we're not recording
        if (!isServiceRunning) {
            closeCamera()
            stopBackgroundThread()
        }
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // We don't stop the service here either - let it run in the background
    }

    @Override
    override fun onBackPressed() {
        // If recording is active, confirm before stopping or minimize app
        if (isServiceRunning) {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Recording in Progress")
                .setMessage("What would you like to do?")
                .setPositiveButton("Continue in Background") { _, _ ->
                    // Just minimize the app without stopping recording
                    moveTaskToBack(true)
                }
                .setNegativeButton("Stop Recording") { _, _ ->
                    stopRecordingService()
                    super.onBackPressed()
                }
                .setNeutralButton("Cancel", null)
                .create()

            alertDialog.show()
        } else {
            super.onBackPressed()
        }
    }

    private fun startBackgroundThread() {
        if (!::backgroundThread.isInitialized || !backgroundThread.isAlive) {
            backgroundThread = HandlerThread("CameraBackground").apply {
                // Set higher priority for smoother camera operations
                priority = Thread.MAX_PRIORITY - 1
                start()
            }
            backgroundHandler = Handler(backgroundThread.looper)
        }
    }

    private fun stopBackgroundThread() {
        if (::backgroundThread.isInitialized && backgroundThread.isAlive) {
            try {
                backgroundThread.quitSafely()
                // Use a timeout to avoid blocking the UI thread too long
                backgroundThread.join(500)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error stopping background thread: ${e.message}")
            }
        }
    }

    private fun checkAndRequestPermissions() {
        // Define required permissions based on Android version
        val basePermissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WAKE_LOCK
        )

        // Add storage permissions for older Android versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            basePermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            basePermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Convert to array
        val permissions = basePermissions.toTypedArray()

        // Log permission check
        Log.d(TAG, "Checking permissions on Android ${Build.VERSION.SDK_INT}")
        permissions.forEach { permission ->
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $permission: ${if (isGranted) "GRANTED" else "DENIED"}")
        }

        // Filter permissions that need to be requested
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        Log.d(TAG, "Permissions to request: ${permissionsToRequest.joinToString()}")

        if (permissionsToRequest.isEmpty()) {
            Log.d(TAG, "All permissions already granted, setting up camera")
            // Start background thread before setting up camera
            startBackgroundThread()
            setupCamera()
        } else {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun setupCamera() {
        if (isAudioOnly) {
            Log.d(TAG, "Audio only mode, skipping camera setup")
            return
        }

        // Check camera permission
        val cameraPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!cameraPermissionGranted) {
            Log.d(TAG, "Camera permission not granted, cannot setup camera")
            return
        }

        // Check audio permission as well since we need it for recording
        val audioPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!audioPermissionGranted) {
            Log.d(TAG, "Audio permission not granted, but proceeding with camera setup")
            // We can still set up the camera preview even without audio permission
        }

        // Ensure the TextureView is available and has a valid size
        if (!cameraPreview.isAvailable || cameraPreview.width == 0 || cameraPreview.height == 0) {
            Log.d(TAG, "Camera preview not ready yet, setting up surface texture listener")
            cameraPreview.surfaceTextureListener = surfaceTextureListener
            return
        }

        // Don't set up camera if it's already set up
        if (cameraDevice != null && captureSession != null) {
            Log.d(TAG, "Camera already set up, skipping")
            return
        }

        // Ensure we have a valid surface
        if (previewSurface == null) {
            Log.d(TAG, "Creating preview surface from texture")
            val texture = cameraPreview.surfaceTexture
            if (texture != null) {
                // Set buffer size to match the TextureView dimensions
                val width = cameraPreview.width
                val height = cameraPreview.height
                Log.d(TAG, "Setting buffer size to ${width}x${height}")
                texture.setDefaultBufferSize(width, height)

                // Create the surface
                previewSurface = Surface(texture)
                Log.d(TAG, "Created preview surface successfully")
            } else {
                Log.e(TAG, "Surface texture is null, cannot setup camera")
                return
            }
        }

        try {
            // Use cached camera ID if available
            val cameraId = findCamera()
            if (cameraId != null) {
                Log.d(TAG, "Opening camera with ID: $cameraId")
                // Use a timeout to avoid blocking if camera is busy
                backgroundHandler.post {
                    try {
                        // Close any existing camera first
                        closeCamera()

                        // Now open the camera
                        cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Error accessing camera: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(this, "Error accessing camera", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception accessing camera: ${e.message}")
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Invalid camera ID: ${e.message}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error opening camera: ${e.message}")
                    }
                }
            } else {
                Log.e(TAG, "No suitable camera found")
                Toast.makeText(this, "No suitable camera found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupCamera: ${e.message}")
        }
    }

    private fun findCamera(): String? {
        try {
            val cameraIds = cameraManager.cameraIdList

            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (useFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return id
                } else if (!useFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }

            // If preferred camera not found, return first available
            if (cameraIds.isNotEmpty()) {
                return cameraIds[0]
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error finding camera: ${e.message}")
        }

        return null
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = cameraPreview.surfaceTexture
            if (texture == null) {
                Log.e(TAG, "SurfaceTexture is null, cannot create preview session")
                return
            }

            // Set optimal buffer size based on display metrics for better performance
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // Calculate optimal preview size
            val previewWidth: Int
            val previewHeight: Int
            if (cameraPreview.width > 0 && cameraPreview.height > 0) {
                // Use actual preview size if available
                previewWidth = cameraPreview.width
                previewHeight = cameraPreview.height
            } else {
                // Use screen size as fallback
                previewWidth = screenWidth
                previewHeight = screenHeight
            }

            // Set the buffer size
            texture.setDefaultBufferSize(previewWidth, previewHeight)

            // Recycle previous surface if it exists
            previewSurface?.release()

            // Create new surface
            previewSurface = Surface(texture)

            // Log the surface creation
            Log.d(TAG, "Created preview surface with size ${previewWidth}x${previewHeight}")

            // Create capture session on background thread to avoid UI jank
            backgroundHandler.post {
                try {
                    // Get the current surface - it might have changed since we posted this task
                    val surface = previewSurface
                    if (surface == null) {
                        Log.e(TAG, "Preview surface is null, cannot create capture session")
                        return@post
                    }

                    // Check if camera device is still valid
                    val camera = cameraDevice
                    if (camera == null) {
                        Log.e(TAG, "Camera device is null, cannot create capture session")
                        return@post
                    }

                    // Create the capture session
                    try {
                        camera.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    // Check if camera is still valid
                                    val currentCamera = cameraDevice
                                    if (currentCamera == null) {
                                        Log.e(TAG, "Camera closed during session configuration")
                                        return
                                    }

                                    captureSession = session
                                    try {
                                        val captureRequestBuilder = currentCamera.createCaptureRequest(
                                            CameraDevice.TEMPLATE_PREVIEW
                                        )
                                        captureRequestBuilder.addTarget(surface)

                                        // Set flash if enabled and available
                                        if (useFlash && !useFrontCamera) {
                                            captureRequestBuilder.set(
                                                CaptureRequest.FLASH_MODE,
                                                CaptureRequest.FLASH_MODE_TORCH
                                            )
                                        }

                                        // Set optimal preview settings for performance
                                        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                        captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)

                                        // Start the preview
                                        session.setRepeatingRequest(
                                            captureRequestBuilder.build(),
                                            null,
                                            backgroundHandler
                                        )

                                        Log.d(TAG, "Camera preview started successfully")
                                    } catch (e: CameraAccessException) {
                                        Log.e(TAG, "Error creating preview: ${e.message}")
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Log.e(TAG, "Failed to configure camera session")
                                    runOnUiThread {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Camera preview configuration failed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            backgroundHandler
                        )
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "Camera was already closed: ${e.message}")
                        // Try to reopen the camera
                        runOnUiThread {
                            closeCamera()
                            setupCamera()
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Invalid surface provided: ${e.message}")
                        // Try to recreate the surface
                        runOnUiThread {
                            previewSurface?.release()
                            previewSurface = Surface(cameraPreview.surfaceTexture)
                            setupCamera()
                        }
                    }
                } catch (e: CameraAccessException) {
                    Log.e(TAG, "Error creating preview session: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in createCameraPreviewSession: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in createCameraPreviewSession: ${e.message}")
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null

        cameraDevice?.close()
        cameraDevice = null
    }

    private fun toggleFlash() {
        if (useFrontCamera) {
            Toast.makeText(this, "Flash not available on front camera", Toast.LENGTH_SHORT).show()
            return
        }

        useFlash = !useFlash
        flashButton.setImageResource(
            if (useFlash) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )

        // Restart camera preview with new flash setting
        closeCamera()
        setupCamera()
    }

    private fun startCameraRecording() {
        Log.d(TAG, "Starting recording with mode: ${if (isAudioOnly) "audio only" else "video"}")

        // Check required permissions before starting
        val requiredPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (!isAudioOnly) {
            requiredPermissions.add(Manifest.permission.CAMERA)
        }

        // Check if we have all required permissions
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Missing required permissions: $missingPermissions")

            // Show a more helpful message based on which permissions are missing
            val message = when {
                missingPermissions.contains(Manifest.permission.CAMERA) &&
                missingPermissions.contains(Manifest.permission.RECORD_AUDIO) ->
                    "Camera and microphone permissions are required"
                missingPermissions.contains(Manifest.permission.CAMERA) ->
                    "Camera permission is required for video recording"
                missingPermissions.contains(Manifest.permission.RECORD_AUDIO) ->
                    "Microphone permission is required for recording"
                else -> "Required permissions are missing"
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // Request the missing permissions
            permissionLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        // Get quality settings from settings manager
        val quality = getVideoQualityFromSettings()
        val fps = settingsManager.getVideoFps()
        Log.d(TAG, "Using FPS from settings: $fps")

        val intent = Intent(this, SecretRecorderService::class.java).apply {
            putExtra("useFrontCamera", useFrontCamera)
            putExtra("isAudioOnly", isAudioOnly)
            putExtra("useFlash", useFlash)
            putExtra("videoWidth", quality.width)
            putExtra("videoHeight", quality.height)
            putExtra("videoBitRate", quality.bitRate)
            putExtra("videoFps", fps)
        }

        // Show privacy indicator dialog for Android 12+ if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            privacyIndicatorManager.showPrivacyIndicatorDialogIfNeeded()
        }

        // Start service and bind to it
        startForegroundService(intent)
        bindToService()

        // Update UI to reflect recording state
        isServiceRunning = true
        updateRecordingUI(true)

        // No overlay functionality anymore
    }

    // Helper method to get video quality from settings
    private fun getVideoQualityFromSettings(): VideoQuality {
        val qualitySetting = settingsManager.getVideoQuality()
        Log.d(TAG, "Reading video quality from settings: $qualitySetting")

        val quality = when (qualitySetting) {
            "480p" -> VideoQuality(854, 480, 2_500_000)
            "720p" -> VideoQuality(1280, 720, 5_000_000)
            "1080p" -> VideoQuality(1920, 1080, 8_000_000)
            "2160p" -> VideoQuality(3840, 2160, 20_000_000)
            else -> VideoQuality(1280, 720, 5_000_000) // Default to 720p
        }

        Log.d(TAG, "Using video resolution: ${quality.width}x${quality.height}, bitrate: ${quality.bitRate}")
        return quality
    }

    private fun stopRecordingService() {
        // Unbind first if bound
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }

        val intent = Intent(this, SecretRecorderService::class.java)
        stopService(intent)

        // Update UI to reflect stopped state
        isServiceRunning = false
        updateRecordingUI(false)
    }

    private fun checkServiceStatus() {
        isServiceRunning = isServiceRunning()
        updateRecordingUI(isServiceRunning)
    }

    // Store animation reference to avoid memory leaks
    private var recordingIndicatorAnimation: ObjectAnimator? = null

    private fun updateRecordingUI(isRecording: Boolean) {
        // Use hardware acceleration for smoother UI updates
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        if (isRecording) {
            // Update recording button - use setImageDrawable for better performance
            startButton.setImageResource(R.drawable.ic_stop)
            startButton.setBackgroundResource(R.drawable.rounded_button_background_active)

            // Update status
            recordingStatus.text = getString(R.string.recording_active)
            recordingIndicator.visibility = View.VISIBLE

            // Start timer with the correct base time from service if available
            recordingTimer.visibility = View.VISIBLE
            if (secretRecorderService != null) {
                val startTime = secretRecorderService!!.getRecordingStartTime()
                if (startTime > 0) {
                    recordingTimer.base = startTime
                } else {
                    recordingTimer.base = SystemClock.elapsedRealtime()
                }
            } else {
                recordingTimer.base = SystemClock.elapsedRealtime()
            }
            recordingTimer.start()

            // Cancel any existing animation to prevent leaks
            recordingIndicatorAnimation?.cancel()

            // Create blinking animation for recording indicator - use hardware acceleration
            recordingIndicatorAnimation = ObjectAnimator.ofFloat(recordingIndicator, "alpha", 1f, 0.3f).apply {
                duration = 750  // Slower animation is less CPU intensive
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                // Use hardware acceleration if available
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    setAutoCancel(true)
                }
                start()
            }

            // Disable recording mode toggle during recording
            recordingModeToggle.isEnabled = false
        } else {
            // Update recording button
            startButton.setImageResource(R.drawable.ic_record)
            startButton.setBackgroundResource(R.drawable.rounded_button_background)

            // Update status
            recordingStatus.text = getString(R.string.not_recording)
            recordingIndicator.visibility = View.INVISIBLE

            // Stop timer
            recordingTimer.stop()
            recordingTimer.visibility = View.GONE

            // Cancel any animations properly to avoid memory leaks
            recordingIndicatorAnimation?.cancel()
            recordingIndicatorAnimation = null
            recordingIndicator.clearAnimation()
            recordingIndicator.alpha = 1f

            // Re-enable recording mode toggle
            recordingModeToggle.isEnabled = true
        }
    }

    private fun openRecordingsBrowser() {
        val intent = Intent(this, RecordingsBrowserActivity::class.java)
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        // Add smooth animation when opening settings
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun showScheduleDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.schedule_recording))
            .setMessage("Schedule recording functionality will be implemented in the next version.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, "Recordings").apply { mkdirs() }
        }
        return mediaDir ?: filesDir
    }

    private fun handleQuickStartIntent() {
        val quickStart = intent.getBooleanExtra("quickStart", false)

        if (quickStart && !isServiceRunning) {
            // Start recording automatically
            Log.d(TAG, "Quick start recording requested")

            // Wait a bit for the camera to initialize, then start recording
            Handler(Looper.getMainLooper()).postDelayed({
                startCameraRecording()
            }, 1000)
        }
    }

    /**
     * More reliable way to check if our service is running
     */
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SecretRecorderService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun bindToService() {
        val intent = Intent(this, SecretRecorderService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // Data class for video quality presets
    data class VideoQuality(val width: Int, val height: Int, val bitRate: Int)
}