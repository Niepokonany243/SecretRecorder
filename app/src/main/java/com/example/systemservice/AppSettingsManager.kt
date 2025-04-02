package com.example.systemservice

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manager class for handling application settings and preferences
 */
class AppSettingsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AppSettingsManager"

        // Keys for SharedPreferences
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_VIDEO_FPS = "video_fps"
        private const val KEY_AUDIO_ONLY = "audio_only"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_PASSWORD = "app_password"
        private const val KEY_APP_PIN = "app_pin"
        private const val KEY_APP_THEME = "app_theme"

        // Default values
        private const val DEFAULT_VIDEO_QUALITY = "720p"
        private const val DEFAULT_VIDEO_FPS = 30
        private const val DEFAULT_AUDIO_ONLY = false
        private const val DEFAULT_APP_LOCK_ENABLED = false
        private const val DEFAULT_APP_THEME = "system" // Options: "light", "dark", "system"
    }

    // Video Quality settings
    fun getVideoQuality(): String {
        val quality = prefs.getString(KEY_VIDEO_QUALITY, DEFAULT_VIDEO_QUALITY) ?: DEFAULT_VIDEO_QUALITY
        Log.d(TAG, "Getting video quality: $quality")
        return quality
    }

    fun setVideoQuality(quality: String) {
        Log.d(TAG, "Setting video quality: $quality")
        prefs.edit().putString(KEY_VIDEO_QUALITY, quality).apply()
    }

    fun getVideoResolution(): Pair<Int, Int> {
        val resolution = when (getVideoQuality()) {
            "480p" -> Pair(640, 480)
            "720p" -> Pair(1280, 720)
            "1080p" -> Pair(1920, 1080)
            "2160p" -> Pair(3840, 2160)
            else -> Pair(1280, 720) // Default to 720p
        }
        Log.d(TAG, "Getting video resolution: ${resolution.first}x${resolution.second}")
        return resolution
    }

    // Video FPS settings
    fun getVideoFps(): Int {
        val fps = prefs.getInt(KEY_VIDEO_FPS, DEFAULT_VIDEO_FPS)
        Log.d(TAG, "Getting video FPS: $fps")
        return fps
    }

    fun setVideoFps(fps: Int) {
        Log.d(TAG, "Setting video FPS: $fps")
        prefs.edit().putInt(KEY_VIDEO_FPS, fps).apply()
    }

    // Audio Only settings
    fun isAudioOnly(): Boolean {
        val audioOnly = prefs.getBoolean(KEY_AUDIO_ONLY, DEFAULT_AUDIO_ONLY)
        Log.d(TAG, "Getting audio only mode: $audioOnly")
        return audioOnly
    }

    fun setAudioOnly(audioOnly: Boolean) {
        Log.d(TAG, "Setting audio only mode: $audioOnly")
        prefs.edit().putBoolean(KEY_AUDIO_ONLY, audioOnly).apply()
    }

    // App Lock settings
    fun isAppLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, DEFAULT_APP_LOCK_ENABLED)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }

    // App PIN/password settings

    // Legacy method for backward compatibility
    fun getAppPassword(): String {
        // First check new PIN key, then fallback to old password key
        val pin = prefs.getString(KEY_APP_PIN, "")
        return if (!pin.isNullOrEmpty()) {
            pin
        } else {
            prefs.getString(KEY_APP_PASSWORD, "") ?: ""
        }
    }

    // New PIN methods
    fun getAppPin(): String {
        // First check new PIN key, then fallback to old password key
        val pin = prefs.getString(KEY_APP_PIN, "")
        return if (!pin.isNullOrEmpty()) {
            pin
        } else {
            prefs.getString(KEY_APP_PASSWORD, "") ?: ""
        }
    }

    // Legacy method for backward compatibility
    fun setAppPassword(password: String) {
        // Save to both keys for compatibility
        prefs.edit()
            .putString(KEY_APP_PASSWORD, password)
            .putString(KEY_APP_PIN, password)
            .apply()
    }

    // New method for setting PIN
    fun setAppPin(pin: String) {
        // Save to both keys for compatibility
        prefs.edit()
            .putString(KEY_APP_PASSWORD, pin)
            .putString(KEY_APP_PIN, pin)
            .apply()
    }



    // Helper method to check if app lock should be shown
    fun shouldShowAppLock(): Boolean {
        return isAppLockEnabled() && getAppPin().isNotEmpty()
    }

    // Debug method to dump all settings
    fun logAllSettings() {
        Log.d(TAG, "Current Settings:")
        Log.d(TAG, "- Video Quality: ${getVideoQuality()}")
        Log.d(TAG, "- Video FPS: ${getVideoFps()}")
        Log.d(TAG, "- Audio Only: ${isAudioOnly()}")

        Log.d(TAG, "- App Lock Enabled: ${isAppLockEnabled()}")
        Log.d(TAG, "- App PIN Set: ${getAppPin().isNotEmpty()}")

        Log.d(TAG, "- App Theme: ${getAppTheme()}")
        Log.d(TAG, "- Dark Mode Active: ${shouldUseDarkMode()}")
    }

    // Add new keys for video encryption and storage location
    private val PREF_VIDEO_ENCRYPTION = "video_encryption"
    private val PREF_STORAGE_LOCATION = "storage_location"

    // Add getters and setters for video encryption
    fun isVideoEncryptionEnabled(): Boolean {
        return prefs.getBoolean(PREF_VIDEO_ENCRYPTION, false)
    }

    fun setVideoEncryptionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_VIDEO_ENCRYPTION, enabled).apply()
    }

    // Add getters and setters for storage location
    fun getStorageLocation(): String {
        return prefs.getString(PREF_STORAGE_LOCATION, "default") ?: "default"
    }

    fun setStorageLocation(location: String) {
        prefs.edit().putString(PREF_STORAGE_LOCATION, location).apply()
    }

    // Theme settings
    fun getAppTheme(): String {
        return prefs.getString(KEY_APP_THEME, DEFAULT_APP_THEME) ?: DEFAULT_APP_THEME
    }

    fun setAppTheme(theme: String) {
        if (theme in listOf("light", "dark", "system")) {
            Log.d(TAG, "Setting app theme: $theme")
            prefs.edit().putString(KEY_APP_THEME, theme).apply()
        } else {
            Log.e(TAG, "Invalid theme value: $theme. Using default.")
            prefs.edit().putString(KEY_APP_THEME, DEFAULT_APP_THEME).apply()
        }
    }

    // Helper method to determine if dark mode should be used
    fun shouldUseDarkMode(): Boolean {
        return when (getAppTheme()) {
            "light" -> false
            "dark" -> true
            else -> { // "system" or any other value
                // Get system dark mode setting
                val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}