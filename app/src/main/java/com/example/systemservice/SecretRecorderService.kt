package com.example.systemservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.app.PendingIntent
import android.os.Binder
import android.os.SystemClock
import android.os.Environment

class SecretRecorderService : Service() {

    companion object {
        private const val TAG = "SecretRecorderService"
        private const val NOTIFICATION_ID = 1234
        private const val CHANNEL_ID = "secret_recorder_channel"

        // Video recording constants
        private const val VIDEO_WIDTH = 1280
        private const val VIDEO_HEIGHT = 720
        private const val VIDEO_FRAME_RATE = 30
        private const val VIDEO_ENCODING_BIT_RATE = 10_000_000 // 10 Mbps
    }

    // Recording variables
    private lateinit var recordingThread: HandlerThread
    private lateinit var recordingHandler: Handler
    private lateinit var wakeLock: PowerManager.WakeLock
    private var mediaRecorder: MediaRecorder? = null

    // Privacy indicator manager
    private lateinit var privacyIndicatorManager: PrivacyIndicatorManager

    // Recording state
    private var isRecording = false
    private var currentVideoFile: File? = null
    private var recordingStartTime: Long = 0 // Add recording start time

    // The camera-related fields
    private var useFrontCamera = true
    private var isAudioOnly = false
    private var useFlash = false

    // Camera components
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var previewSurface: Surface? = null

    // Executor for scheduling tasks
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Video quality settings
    private var videoWidth = 1280
    private var videoHeight = 720
    private var videoBitRate = 5_000_000
    private var videoFps = 30  // Default FPS

    // Add a retry mechanism for camera errors
    private var cameraRetryCount = 0
    private val MAX_CAMERA_RETRIES = 3

    // Add binder for activity communication
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SecretRecorderService = this@SecretRecorderService

        fun getRecordingStartTime(): Long = recordingStartTime

        fun isRecording(): Boolean = isRecording
    }

    private val cameraCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            Log.d(TAG, "Camera opened")
            // Reset retry count on successful open
            cameraRetryCount = 0

            // Add a slight delay before starting recording to ensure camera is fully initialized
            recordingHandler.postDelayed({
                startVideoRecording()
            }, 500) // 500ms delay
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            closeCamera()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            closeCamera()

            // Try to reopen camera if error occurs, with a maximum retry count
            if (cameraRetryCount < MAX_CAMERA_RETRIES) {
                cameraRetryCount++
                Log.d(TAG, "Retrying camera open (attempt $cameraRetryCount)")

                // Delay slightly before retry
                Handler(Looper.getMainLooper()).postDelayed({
                    openCamera()
                }, 1000)
            } else {
                Log.e(TAG, "Max camera retries reached, stopping service")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize camera manager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Initialize privacy indicator manager
        privacyIndicatorManager = PrivacyIndicatorManager(this)

        // Initialize recording thread with higher priority for smoother recording
        recordingThread = HandlerThread("RecordingThread", Thread.NORM_PRIORITY + 1)
        recordingThread.start()
        recordingHandler = Handler(recordingThread.looper)

        // Create a low-priority notification channel
        createNotificationChannel()

        // Acquire wake lock to keep CPU running when screen is off
        // Use a timeout to avoid battery drain (30 minutes max)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SecretRecorder:WakeLock"
        )
        wakeLock.acquire(30 * 60 * 1000L) // 30 minutes timeout
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Get parameters from intent
        if (intent != null && intent.extras != null && !isRecording) {
            // Only set parameters if intent has extras (not when restarting from system)
            // AND we're not already recording
            useFrontCamera = intent.getBooleanExtra("useFrontCamera", true)
            isAudioOnly = intent.getBooleanExtra("isAudioOnly", false)
            useFlash = intent.getBooleanExtra("useFlash", false)
            videoWidth = intent.getIntExtra("videoWidth", VIDEO_WIDTH)
            videoHeight = intent.getIntExtra("videoHeight", VIDEO_HEIGHT)
            videoBitRate = intent.getIntExtra("videoBitRate", VIDEO_ENCODING_BIT_RATE)
            videoFps = intent.getIntExtra("videoFps", VIDEO_FRAME_RATE)

            // Log the received parameters
            Log.d(TAG, "Received recording parameters: resolution=${videoWidth}x${videoHeight}, " +
                  "bitRate=$videoBitRate, fps=$videoFps, audioOnly=$isAudioOnly")
        }

        // Create and show notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Show privacy indicator dialog for Android 12+ if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            privacyIndicatorManager.showPrivacyIndicatorDialogIfNeeded()
        }

        // Only start new recording if not already recording
        if (!isRecording) {
            if (isAudioOnly) {
                startAudioRecording()
            } else {
                openCamera()
            }

            // No overlay functionality anymore
        } else {
            Log.d(TAG, "Service already recording, not starting new recording")
        }

        // If service gets killed, restart with last intent
        return START_REDELIVER_INTENT
    }

    private fun openCamera() {
        try {
            // Find the front or back camera
            val cameraId = findCamera()

            if (cameraId != null) {
                // Open the camera (requires camera permission)
                cameraManager.openCamera(cameraId, cameraCallback, recordingHandler)
            } else {
                Log.e(TAG, "Could not find suitable camera")
                stopSelf()
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Could not access camera: ${e.message}")
            stopSelf()
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted: ${e.message}")
            stopSelf()
        }
    }

    private fun findCamera(): String? {
        try {
            // Get list of all cameras
            val cameraIds = cameraManager.cameraIdList

            // Iterate through cameras to find front or back
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                // Check if it's the camera we want (front or back)
                if (useFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return id
                } else if (!useFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }

            // If we didn't find the preferred camera, return the first one
            if (cameraIds.isNotEmpty()) {
                return cameraIds[0]
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to get camera IDs: ${e.message}")
        }

        return null
    }

    private fun closeCamera() {
        // Only try to stop the mediaRecorder if we're actively recording
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                isRecording = false
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping media recorder: ${e.message}")
            }
        }

        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null

        cameraDevice?.close()
        cameraDevice = null

        previewSurface?.release()
        previewSurface = null
    }

    private fun startVideoRecording() {
        if (isRecording) return

        try {
            // Create output file
            val outputDir = getOutputDirectory()
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // Create output file with timestamp and quality info
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val qualityInfo = "${videoWidth}x${videoHeight}_${videoFps}fps"
            val videoFile = File(outputDir, "video_${timestamp}_${qualityInfo}.mp4")
            currentVideoFile = videoFile

            Log.d(TAG, "Creating video file with name: ${videoFile.name}")

            if (!setupMediaRecorder(videoFile)) {
                Log.e(TAG, "Failed to setup MediaRecorder, aborting recording")
                stopSelf()
                return
            }

            if (!setupCameraForRecording()) {
                Log.e(TAG, "Failed to setup camera for recording, aborting")
                mediaRecorder?.reset()
                mediaRecorder?.release()
                mediaRecorder = null
                stopSelf()
                return
            }

            // Start recording
            try {
                // Check if file can be written to before starting
                if (!videoFile.canWrite() && videoFile.parentFile?.canWrite() == true) {
                    Log.d(TAG, "File cannot be written to directly, but parent directory is writable")
                }

                // Log file details before starting
                Log.d(TAG, "File path: ${videoFile.absolutePath}")
                Log.d(TAG, "File exists: ${videoFile.exists()}")
                Log.d(TAG, "Parent directory exists: ${videoFile.parentFile?.exists()}")
                Log.d(TAG, "Parent directory writable: ${videoFile.parentFile?.canWrite()}")

                // Start recording
                mediaRecorder?.start()
                isRecording = true
                recordingStartTime = SystemClock.elapsedRealtime() // Store start time

                Log.d(TAG, "Video recording started to ${videoFile.absolutePath}")

                // Schedule automatic stop after a reasonable duration (30 minutes)
                executor.schedule({
                    if (isRecording) {
                        Log.d(TAG, "Automatic recording stop after 30 minutes")
                        stopVideoRecording()
                        // Restart recording with a new file
                        startVideoRecording()
                    }
                }, 30, TimeUnit.MINUTES)

                // Add a periodic check to ensure recording is still working
                executor.scheduleAtFixedRate({
                    if (isRecording) {
                        // Check if file size is increasing
                        val fileSize = videoFile.length()
                        Log.d(TAG, "Current recording file size: $fileSize bytes")

                        // If file doesn't exist or has zero size after some time, restart recording
                        if (!videoFile.exists() || (SystemClock.elapsedRealtime() - recordingStartTime > 10000 && fileSize == 0L)) {
                            Log.e(TAG, "Recording file not growing, restarting recording")
                            stopVideoRecording()
                            startVideoRecording()
                        }
                    }
                }, 10, 30, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MediaRecorder: ${e.message}")
                e.printStackTrace()

                // Clean up resources
                mediaRecorder?.reset()
                mediaRecorder?.release()
                mediaRecorder = null

                // Try to delete the failed recording file
                try {
                    if (videoFile.exists()) {
                        val deleted = videoFile.delete()
                        Log.d(TAG, "Deleted failed recording file: $deleted")
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error deleting failed recording file: ${ex.message}")
                }

                stopSelf()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting video recording: ${e.message}")
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun setupMediaRecorder(outputFile: File): Boolean {
        try {
            // Release any existing MediaRecorder to avoid memory leaks
            mediaRecorder?.release()

            // Create new MediaRecorder instance
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            Log.d(TAG, "Setting up MediaRecorder with resolution=${videoWidth}x${videoHeight}, " +
                  "frameRate=$videoFps, bitRate=$videoBitRate")

            // Calculate appropriate bitrate based on resolution and fps if not explicitly set
            // Use a more efficient bitrate calculation for better performance
            val calculatedBitRate = if (videoBitRate <= 0) {
                calculateBitrateForResolution(videoWidth, videoHeight, videoFps)
            } else {
                videoBitRate
            }

            Log.d(TAG, "Using bitrate: $calculatedBitRate for resolution ${videoWidth}x${videoHeight} at $videoFps fps")

            // Use a more efficient profile for better performance
            val profile = when {
                videoHeight >= 1080 -> MediaRecorder.VideoEncoder.H264
                else -> MediaRecorder.VideoEncoder.H264 // Use H264 for all resolutions for compatibility
            }

            // Adjust frame rate based on resolution for better performance
            val adjustedFrameRate = when {
                videoHeight >= 1080 && videoFps > 30 -> 30 // Limit 1080p to 30fps for better performance
                videoHeight >= 720 && videoFps > 60 -> 60 // Limit 720p to 60fps
                else -> videoFps
            }

            mediaRecorder?.apply {
                // Step 1: Set sources
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)

                // Step 2: Set output format
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                // Step 3: Set output file
                setOutputFile(outputFile.absolutePath)

                // Step 4: Set video encoding parameters - ORDER MATTERS for some devices

                // Set encoder first
                setVideoEncoder(profile)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                // Then set parameters
                setVideoSize(videoWidth, videoHeight)

                // Explicitly set frame rate - critical step for FPS control
                setVideoFrameRate(adjustedFrameRate)
                Log.d(TAG, "Explicitly set frame rate to $adjustedFrameRate fps")

                // Set video encoding bitrate
                setVideoEncodingBitRate(calculatedBitRate)

                // Audio encoding parameters - use more efficient settings
                setAudioEncodingBitRate(96000) // Reduced from 128000 for better performance
                setAudioSamplingRate(44100)

                // Set camera orientation
                setOrientationHint(if (useFrontCamera) 270 else 90)

                // Step 5: Prepare recorder
                prepare()

                Log.d(TAG, "MediaRecorder prepared successfully with frameRate=$adjustedFrameRate")
            }

            // Create a surface for the MediaRecorder
            previewSurface = mediaRecorder?.surface

            if (previewSurface == null) {
                throw IllegalStateException("Failed to get MediaRecorder surface")
            }

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaRecorder: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // Helper to calculate appropriate bitrate based on resolution and frame rate
    private fun calculateBitrateForResolution(width: Int, height: Int, fps: Int): Int {
        val pixelCount = width * height
        val baseBitrate = when {
            pixelCount >= 3840 * 2160 -> 35_000_000  // 4K UHD
            pixelCount >= 1920 * 1080 -> 8_000_000   // Full HD
            pixelCount >= 1280 * 720 -> 5_000_000    // HD
            pixelCount >= 720 * 480 -> 2_500_000     // SD
            else -> 1_500_000                        // Lower than SD
        }

        // Adjust for frame rate (baseline is 30fps)
        val fpsMultiplier = fps / 30.0
        return (baseBitrate * fpsMultiplier).toInt()
    }

    private fun setupCameraForRecording(): Boolean {
        try {
            val cameraDevice = this.cameraDevice
            if (cameraDevice == null) {
                Log.e(TAG, "Camera not available")
                return false
            }

            val previewSurface = this.previewSurface
            if (previewSurface == null) {
                Log.e(TAG, "Recording surface not available")
                return false
            }

            // Get camera characteristics to check supported FPS ranges
            val cameraId = findCamera()
            if (cameraId == null) {
                Log.e(TAG, "Camera not found")
                return false
            }

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            // Get supported FPS ranges for this camera
            val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val targetFpsRange = getOptimalFpsRange(videoFps, fpsRanges)

            Log.d(TAG, "Selected FPS range: $targetFpsRange for target FPS: $videoFps")
            Log.d(TAG, "Available FPS ranges: ${fpsRanges?.contentToString()}")

            try {
                // Create a list of surfaces for the capture session
                val surfaces = ArrayList<Surface>().apply {
                    // Add the preview surface
                    add(previewSurface)

                    // Create a dummy SurfaceTexture to maintain camera preview
                    val dummyTexture = SurfaceTexture(0)
                    dummyTexture.setDefaultBufferSize(videoWidth, videoHeight)
                    val dummySurface = Surface(dummyTexture)
                    add(dummySurface)
                }

                // Create the capture session
                cameraDevice.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            // Check if camera device is still valid
                            val currentCamera = cameraDevice
                            if (currentCamera == null) {
                                Log.e(TAG, "Camera closed during session configuration")
                                stopSelf()
                                return
                            }

                            // Create a capture request with TEMPLATE_RECORD which is optimized for video recording
                            val captureRequestBuilder = currentCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                // Add the preview surface as the target
                                addTarget(previewSurface)

                                // Set necessary capture request parameters for video recording
                                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

                                // Set explicit FPS range for recording
                                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange)

                                // Set video stabilization if available
                                set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                            }

                            // Start the capture session with repeating request
                            session.setRepeatingRequest(captureRequestBuilder.build(), null, recordingHandler)

                            Log.d(TAG, "Camera capture session started successfully for video recording with FPS range: $targetFpsRange")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error creating capture request: ${e.message}")
                            stopSelf()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Failed to configure camera capture session")
                    stopSelf()
                }
            }, recordingHandler)

                return true

            } catch (e: IllegalStateException) {
                Log.e(TAG, "Camera was already closed: ${e.message}")
                return false
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid surface provided: ${e.message}")
                return false
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Camera access error: ${e.message}")
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up camera for recording: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // Improved helper method to get optimal FPS range based on device capabilities
    private fun getOptimalFpsRange(targetFps: Int, availableRanges: Array<android.util.Range<Int>>?): android.util.Range<Int> {
        if (availableRanges == null || availableRanges.isEmpty()) {
            // Default range if no available ranges
            return android.util.Range(Math.min(15, targetFps), targetFps)
        }

        // First try to find a range with exactly our target FPS as the upper bound
        var exactMatch = availableRanges.firstOrNull { it.upper == targetFps }
        if (exactMatch != null) {
            return exactMatch
        }

        // Next, find the range that can include our target FPS
        var containingRange = availableRanges.firstOrNull {
            it.lower <= targetFps && it.upper >= targetFps
        }
        if (containingRange != null) {
            return containingRange
        }

        // Otherwise, find the range with the closest upper bound to our target
        return availableRanges.sortedBy { Math.abs(it.upper - targetFps) }.first()
    }

    private fun stopVideoRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            Log.d(TAG, "Video recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping video recording: ${e.message}")

            // Delete failed recording file
            currentVideoFile?.apply {
                if (exists()) {
                    delete()
                    Log.d(TAG, "Deleted failed recording file")
                }
            }
        }

        isRecording = false
    }

    private fun getOutputDirectory(): File {
        // Try multiple storage options for better compatibility with MIUI and other custom ROMs

        // Option 1: Use external media dirs (primary option)
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, "Recordings").apply {
                if (!exists()) {
                    val success = mkdirs()
                    Log.d(TAG, "Creating directory ${absolutePath}, success: $success")
                }
            }
        }

        // Option 2: Use external files dir as fallback
        val externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let {
            File(it, "Recordings").apply {
                if (!exists()) {
                    val success = mkdirs()
                    Log.d(TAG, "Creating external files directory ${absolutePath}, success: $success")
                }
            }
        }

        // Option 3: Use internal storage as last resort
        val internalDir = File(filesDir, "Recordings").apply {
            if (!exists()) {
                val success = mkdirs()
                Log.d(TAG, "Creating internal directory ${absolutePath}, success: $success")
            }
        }

        // Use the first available directory
        val selectedDir = when {
            mediaDir != null && mediaDir.canWrite() -> mediaDir
            externalFilesDir != null && externalFilesDir.canWrite() -> externalFilesDir
            internalDir.canWrite() -> internalDir
            else -> filesDir // Last resort
        }

        Log.d(TAG, "Selected output directory: ${selectedDir.absolutePath}")
        return selectedDir
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "System Background Service"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Create an intent to open MainActivity when notification is tapped
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_record)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startAudioRecording() {
        if (isRecording) return

        try {
            // Create output file
            val outputDir = getOutputDirectory()
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // Create output file with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val qualityInfo = "audio_${128}kbps"
            val audioFile = File(outputDir, "audio_${timestamp}_${qualityInfo}.m4a")
            currentVideoFile = audioFile

            Log.d(TAG, "Creating audio file with name: ${audioFile.name}")

            // Setup media recorder for audio only
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            try {
                mediaRecorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(audioFile.absolutePath)
                    prepare()
                }

                // Check if file can be written to before starting
                if (!audioFile.canWrite() && audioFile.parentFile?.canWrite() == true) {
                    Log.d(TAG, "Audio file cannot be written to directly, but parent directory is writable")
                }

                // Log file details before starting
                Log.d(TAG, "Audio file path: ${audioFile.absolutePath}")
                Log.d(TAG, "Audio file exists: ${audioFile.exists()}")
                Log.d(TAG, "Audio parent directory exists: ${audioFile.parentFile?.exists()}")
                Log.d(TAG, "Audio parent directory writable: ${audioFile.parentFile?.canWrite()}")

                // Start recording
                mediaRecorder?.start()
                isRecording = true
                recordingStartTime = SystemClock.elapsedRealtime() // Store start time
                Log.d(TAG, "Audio recording started to ${audioFile.absolutePath}")

                // Schedule automatic stop after a reasonable duration (30 minutes)
                executor.schedule({
                    if (isRecording) {
                        Log.d(TAG, "Automatic recording stop after 30 minutes")
                        stopVideoRecording() // This works for audio too
                        // Restart recording with a new file
                        startAudioRecording()
                    }
                }, 30, TimeUnit.MINUTES)

                // Add a periodic check to ensure recording is still working
                executor.scheduleAtFixedRate({
                    if (isRecording) {
                        // Check if file size is increasing
                        val fileSize = audioFile.length()
                        Log.d(TAG, "Current audio recording file size: $fileSize bytes")

                        // If file doesn't exist or has zero size after some time, restart recording
                        if (!audioFile.exists() || (SystemClock.elapsedRealtime() - recordingStartTime > 10000 && fileSize == 0L)) {
                            Log.e(TAG, "Audio recording file not growing, restarting recording")
                            stopVideoRecording() // This works for audio too
                            startAudioRecording()
                        }
                    }
                }, 10, 30, TimeUnit.SECONDS)

            } catch (e: Exception) {
                Log.e(TAG, "Error preparing/starting audio recording: ${e.message}")
                e.printStackTrace()

                // Clean up resources
                mediaRecorder?.reset()
                mediaRecorder?.release()
                mediaRecorder = null

                // Delete failed recording file
                if (audioFile.exists()) {
                    audioFile.delete()
                    Log.d(TAG, "Deleted failed audio recording file")
                }

                stopSelf()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in audio recording setup: ${e.message}")
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        // First stop recording properly
        if (isRecording) {
            stopVideoRecording()
        }

        // Then clean up resources
        closeCamera()

        // Shutdown executor
        executor.shutdown()

        // Release wake lock
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }

        // Stop recording thread
        if (::recordingThread.isInitialized) {
            recordingThread.quitSafely()
        }

        // No overlay functionality anymore
    }
}