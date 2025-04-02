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
        
        // Apply theme settings
        applyTheme()
        
        // Schedule the recording worker
        RecordingWorker.scheduleWork(this)
    }
    
    private fun applyTheme() {
        val themeMode = when (settingsManager.getThemeMode()) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }
    
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
    }
} 