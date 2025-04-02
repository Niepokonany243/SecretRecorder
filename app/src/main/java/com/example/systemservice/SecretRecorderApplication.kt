package com.example.systemservice

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration

class SecretRecorderApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "SecretRecorderApp"
    }

    private lateinit var settingsManager: AppSettingsManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application created")

        // Initialize settings manager
        settingsManager = AppSettingsManager(this)

        // Apply the theme based on settings
        applyTheme()

        // Schedule the recording worker
        RecordingWorker.scheduleWork(this)
    }

    /**
     * Apply the theme based on user settings
     */
    private fun applyTheme() {
        when (settingsManager.getAppTheme()) {
            "light" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            "dark" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> { // "system"
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }



    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
    }
}