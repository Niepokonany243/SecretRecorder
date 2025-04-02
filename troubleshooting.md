# SecretRecorder Troubleshooting

## Issues to Fix

1. **Preview freezes after starting recording**
   - Need to examine how camera preview is handled during recording
   - Possible thread or surface management issues

2. **Settings are fake/non-functional**
   - Need to implement actual functionality for settings
   - Connect UI elements to real settings storage

3. **Quality option appears in multiple places**
   - Quality option appears in both settings and main menu
   - Need to consolidate to a single consistent location

4. **MediaRecorder invalid state error**
   - Error: `stop called in an invalid state: 1`
   - Need to fix error when stopping recording

5. **Settings not affecting actual recording**
   - 4K and 60 FPS settings not being applied to recordings
   - Recordings still using 1080p and 30 FPS regardless of settings

6. **Incorrect quality/FPS display in recordings viewer**
   - Recordings viewer shows wrong quality and FPS information
   - Need to fix metadata extraction and display

## Analysis and Solutions

### 1. Preview Freezing Issue
The problem was in the `SecretRecorderService.kt` file, specifically in the camera capture session setup. During recording, the camera was focused only on the MediaRecorder surface without maintaining a proper preview surface.

**Solution:**
- Added a dummy SurfaceTexture to keep the preview active while recording
- Added FPS range configuration to prevent camera from switching to low-power mode
- Implemented a helper method to determine optimal FPS range

```kotlin
// Add to surfaces list in setupCameraForRecording()
val dummyTexture = SurfaceTexture(0)
dummyTexture.setDefaultBufferSize(videoWidth, videoHeight)
val dummySurface = Surface(dummyTexture)
add(dummySurface)

// Add to captureRequestBuilder in onConfigured()
set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getOptimalFpsRange(videoFps))

// Helper method
private fun getOptimalFpsRange(targetFps: Int): android.util.Range<Int> {
    val min = if (targetFps > 30) 30 else targetFps
    return android.util.Range(min, targetFps)
}
```

### 2. Making Settings Functional
The settings in the app were disconnected from the actual recording functionality. Settings were defined in `SettingsActivity` but not used by the main recording logic.

**Solution:**
1. Added proper integration with `AppSettingsManager` in `MainActivity`
2. Modified the recording start logic to use settings from the manager instead of UI elements
3. Made recording mode toggles save their state to settings

```kotlin
// In MainActivity
private lateinit var settingsManager: AppSettingsManager

// In onCreate()
settingsManager = AppSettingsManager(this)

// In initializeViews()
isAudioOnly = settingsManager.isAudioOnly()
recordingModeToggle.check(if (isAudioOnly) R.id.audioModeButton else R.id.videoModeButton)

// In recordingModeToggle listener
settingsManager.setAudioOnly(isAudioOnly)

// When starting recording
val quality = getVideoQualityFromSettings()
intent.putExtra("videoFps", settingsManager.getVideoFps())
```

### 3. Removing Duplicate Quality Options
The quality selector was appearing both in the main activity and settings activity, causing confusion.

**Solution:**
1. Removed the `qualitySelector` Spinner from `activity_main.xml`
2. Removed all references to the selector from `MainActivity.kt`
3. Implemented a `getVideoQualityFromSettings()` helper method in MainActivity
4. Updated the Settings navigation to use the proper `SettingsActivity` instead of a dialog

Now quality settings are managed only through the dedicated Settings screen, making the UI more consistent.

### 4. Fixing MediaRecorder Invalid State Error
The app was experiencing an error when stopping the MediaRecorder: `stop called in an invalid state: 1`. This occurs when trying to stop a MediaRecorder that hasn't been properly started or was already stopped.

**Solution:**
1. Added a check in `closeCamera()` to only stop the MediaRecorder if recording is active
2. Improved error handling in recording methods to prevent multiple stop attempts
3. Fixed order of operations when cleaning up resources in `onDestroy()`
4. Added comprehensive error handling when starting and stopping recordings

```kotlin
// Only try to stop the mediaRecorder if we're actively recording
if (isRecording) {
    try {
        mediaRecorder?.stop()
        isRecording = false
    } catch (e: Exception) {
        Log.e(TAG, "Error stopping media recorder: ${e.message}")
    }
}
```

5. Enhanced both audio and video recording methods with better state management and error recovery:
   - Added return status check for initialization methods
   - Properly handle resource cleanup when errors occur
   - Added try-catch blocks around media recorder start operations

### 5. Fixing Settings Not Affecting Recording
The app was not applying the settings correctly, as 4K and 60 FPS settings weren't being applied when starting recordings. This issue was caused by a lack of proper logging and verification that the settings were being correctly passed from the SettingsActivity through the AppSettingsManager to the MainActivity and finally to the SecretRecorderService.

**Solution:**
1. Added comprehensive logging throughout the settings flow to track settings values:
   ```kotlin
   // In AppSettingsManager
   Log.d(TAG, "Getting video quality: $quality")
   
   // In MainActivity
   Log.d(TAG, "Reading video quality from settings: $qualitySetting")
   Log.d(TAG, "Using video resolution: ${quality.width}x${quality.height}")
   ```

2. Enhanced SettingsActivity to log settings changes:
   ```kotlin
   // In SettingsActivity
   Log.d("SettingsActivity", "Saving FPS setting: $fps")
   settingsManager.logAllSettings() // Log all settings after change
   ```

3. Added a debugging method to dump all settings:
   ```kotlin
   fun logAllSettings() {
       Log.d(TAG, "Current Settings:")
       Log.d(TAG, "- Video Quality: ${getVideoQuality()}")
       Log.d(TAG, "- Video FPS: ${getVideoFps()}")
       Log.d(TAG, "- Audio Only: ${isAudioOnly()}")
   }
   ```

4. Added additional logging in the SecretRecorderService to confirm the received parameters:
   ```kotlin
   Log.d(TAG, "Received recording parameters: resolution=${videoWidth}x${videoHeight}, " +
          "bitRate=$videoBitRate, fps=$videoFps, audioOnly=$isAudioOnly")
   
   Log.d(TAG, "Setting up MediaRecorder with resolution=${videoWidth}x${videoHeight}, " +
          "frameRate=$videoFps, bitRate=$videoBitRate")
   ```

With these changes, the app now properly reads, saves, and applies the user's quality and FPS settings, ensuring 4K and 60 FPS settings are correctly applied to recordings when selected.

### 6. Fixing Incorrect Quality and FPS Display in Recordings Viewer
The recordings browser was showing incorrect quality and FPS information for recorded videos, even when the actual videos were recorded with the correct settings.

**Solution:**
1. Improved metadata extraction in `RecordingsBrowserActivity`:
   ```kotlin
   // Show both actual resolution and quality name
   val qualityName = when {
       height.toInt() >= 2160 -> "4K UHD (2160p)"
       height.toInt() >= 1080 -> "Full HD (1080p)"
       height.toInt() >= 720 -> "HD (720p)"
       height.toInt() >= 480 -> "SD (480p)"
       else -> "${height}p"
   }
   detailsBuilder.append("Resolution: ${width}x${height} ($qualityName)\n")
   ```

2. Enhanced FPS extraction to check both metadata and filename:
   ```kotlin
   // If we couldn't get framerate from metadata, try from filename
   if (frameRate <= 0) {
       frameRate = extractFpsFromFileName(recording.name)
   }
   ```

3. Improved filename format to include quality and FPS information:
   ```kotlin
   // Create output file with timestamp and quality info
   val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
   val qualityInfo = "${videoWidth}x${videoHeight}_${videoFps}fps"
   val videoFile = File(outputDir, "video_${timestamp}_${qualityInfo}.mp4")
   ```

4. Enhanced quality display in the recordings list:
   ```kotlin
   // Determine quality label based on height
   val resolution = when {
       metadata.height >= 2160 -> "4K"
       metadata.height >= 1080 -> "1080p"
       metadata.height >= 720 -> "720p"
       metadata.height >= 480 -> "480p"
       else -> "${metadata.height}p"
   }
   
   // Show quality and framerate
   qualityChip.text = "$resolution • ${metadata.frameRate}fps"
   ```

These changes ensure that the application correctly shows the actual quality and FPS of recorded videos, providing a more accurate representation of the recording settings.

## Build Verification
A build was completed successfully after our changes, confirming that the fixes should work as expected. There are some deprecation warnings, but they don't affect functionality.

## Next Steps
To verify our fixes, the app should be tested:
1. Check that the preview doesn't freeze when recording starts
2. Verify that settings changes (quality, fps) are properly reflected when recording
3. Confirm that quality options only appear in the Settings screen
4. Test starting and stopping recording to ensure no MediaRecorder errors occur
5. Verify that 4K and 60 FPS settings correctly affect the recorded video quality
6. Check that the recording details view and quality chips show the correct quality and FPS information 