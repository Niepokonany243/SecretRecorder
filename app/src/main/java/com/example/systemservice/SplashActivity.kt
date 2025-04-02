package com.example.systemservice

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var settingsManager: AppSettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set app as in foreground when starting
        SecretRecorderBaseActivity.isAppInForeground = true
        
        // Initialize settings manager
        settingsManager = AppSettingsManager(this)
        
        // Immediately proceed to next activity without delay
        if (settingsManager.isAppLockEnabled()) {
            // Go to PIN lock screen
            val intent = Intent(this, PinLockActivity::class.java)
            intent.putExtra("mode", PinLockActivity.MODE_VERIFY)
            startActivity(intent)
        } else {
            // Go directly to main activity
            // Set just unlocked flag to avoid PIN check after splash
            SecretRecorderBaseActivity.isJustUnlocked = true
            startActivity(Intent(this, MainActivity::class.java))
        }
        
        // Close splash activity
        finish()
    }
} 