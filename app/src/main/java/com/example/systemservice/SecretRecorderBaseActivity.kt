package com.example.systemservice

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import android.view.LayoutInflater
import androidx.appcompat.widget.Toolbar

/**
 * Base activity that handles app lock functionality
 */
abstract class SecretRecorderBaseActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: AppSettingsManager
    
    companion object {
        // Flag to prevent app lock check cycle after successful unlock
        var isJustUnlocked = false
        
        // Flag to track if app is in foreground
        var isAppInForeground = false
        
        // Last time the app was active to detect long background time
        var lastActiveTime = System.currentTimeMillis()
        
        // Time threshold for requiring PIN check (5 minutes)
        const val PIN_CHECK_THRESHOLD_MS = 5 * 60 * 1000L  
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize settings manager
        settingsManager = AppSettingsManager(this)
        
        // Enable back/up navigation in all activities that extend this class
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    override fun onResume() {
        super.onResume()
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastActive = currentTime - lastActiveTime
        
        // Only check PIN if app has been in background for longer than threshold
        if (!isAppInForeground || timeSinceLastActive > PIN_CHECK_THRESHOLD_MS) {
            isAppInForeground = true
            checkAppLock()
        }
        
        // Update last active time
        lastActiveTime = currentTime
    }
    
    override fun onPause() {
        super.onPause()
        // Update last active time when pausing
        lastActiveTime = System.currentTimeMillis()
    }
    
    /**
     * Check if app lock is enabled and redirect to lock screen if needed
     */
    private fun checkAppLock() {
        // Skip check for the PinLockActivity itself and SplashActivity
        if (this.javaClass.name.contains("PinLockActivity") || this.javaClass.name.contains("SplashActivity")) {
            return
        }
        
        // Skip check if we just unlocked the app
        if (isJustUnlocked) {
            isJustUnlocked = false
            return
        }
        
        // Check if app lock is enabled and PIN is set
        if (settingsManager.shouldShowAppLock()) {
            val intent = Intent(this, PinLockActivity::class.java)
            intent.putExtra("mode", PinLockActivity.MODE_VERIFY)
            startActivity(intent)
            // Use overridePendingTransition to create smooth transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
    
    /**
     * Setup back button handling with smooth animation
     */
    protected fun setupBackNavigation() {
        // Add callback for back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                // Use smooth animation when going back
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        })
    }
    
    /**
     * Call this method for activities that support up navigation
     */
    protected fun setupActionBar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }
} 