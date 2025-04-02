package com.example.systemservice

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class PinLockActivity : SecretRecorderBaseActivity() {

    companion object {
        const val MODE_VERIFY = "verify"
        const val MODE_SET = "set"
        const val MODE_CHANGE = "change"
    }

    // UI references
    private lateinit var titleText: TextView
    private lateinit var messageText: TextView
    private lateinit var pinDots: Array<View>
    private lateinit var forgotPinText: TextView
    private val numberButtons = ArrayList<Button>()
    private lateinit var buttonClear: Button
    private lateinit var buttonDelete: Button

    // PIN state
    private var currentPin = StringBuilder()
    private var confirmPin = StringBuilder()
    private var pinMode = MODE_VERIFY
    private var attemptCount = 0
    private val MAX_ATTEMPTS = 5

    // Settings manager for PIN storage
    private lateinit var settingsManager: AppSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply theme based on settings
        applyTheme()
        
        setContentView(R.layout.activity_pin_lock)

        // Only add back button in Change PIN mode
        if (intent.getStringExtra("mode") == MODE_CHANGE) {
            setupActionBar()
            setupBackNavigation()
        }

        // Initialize settings manager
        settingsManager = AppSettingsManager(this)

        // Get the operating mode (verify, set, change)
        pinMode = intent.getStringExtra("mode") ?: MODE_VERIFY

        // Initialize views
        initializeViews()
        
        // Set up the UI based on the current mode
        setupUIForMode()
        
        // Setup button click listeners
        setupClickListeners()
    }

    override fun finish() {
        super.finish()
        // Add smooth animation when exiting
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun applyTheme() {
        // Apply theme based on settings
        val settingsManager = AppSettingsManager(this)
        val theme = settingsManager.getThemeMode()
        
        when (theme) {
            "dark" -> {
                setTheme(R.style.AppTheme_Dark)
                // Apply dark styling to PIN dots
                updatePinDotsForDarkMode(true)
            }
            "light" -> {
                setTheme(R.style.AppTheme_Light)
                // Apply light styling to PIN dots
                updatePinDotsForDarkMode(false)
            }
            else -> {
                // Use system default
                // Check if we're in night mode
                val nightModeFlags = resources.configuration.uiMode and 
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                val isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                
                if (isDarkMode) {
                    setTheme(R.style.AppTheme_Dark)
                } else {
                    setTheme(R.style.AppTheme_Light)
                }
                
                // Apply styling to PIN dots based on current theme
                updatePinDotsForDarkMode(isDarkMode)
            }
        }
    }
    
    private fun updatePinDotsForDarkMode(isDarkMode: Boolean) {
        // No UI elements are created yet, so we'll need to update these in initializeViews()
    }

    private fun initializeViews() {
        titleText = findViewById(R.id.titleText)
        messageText = findViewById(R.id.messageText)
        
        // If forgotPinText is present in the layout
        forgotPinText = findViewById(R.id.forgotPinText)
        
        // Initialize PIN dots
        pinDots = arrayOf(
            findViewById(R.id.pinDot1),
            findViewById(R.id.pinDot2),
            findViewById(R.id.pinDot3),
            findViewById(R.id.pinDot4)
        )
        
        // Initialize number buttons
        for (i in 0..9) {
            val buttonId = resources.getIdentifier("pinButton$i", "id", packageName)
            numberButtons.add(findViewById(buttonId))
        }
        
        // Update these to use the IDs that exist in our layout
        buttonClear = findViewById(R.id.pinButtonClear)
        buttonDelete = findViewById(R.id.pinButtonDelete)
    }

    private fun setupUIForMode() {
        when (pinMode) {
            MODE_VERIFY -> {
                titleText.text = "Enter PIN"
                messageText.text = "Enter your PIN to unlock the app"
                forgotPinText.visibility = View.VISIBLE
            }
            MODE_SET -> {
                titleText.text = "Set PIN"
                messageText.text = "Enter a 4-digit PIN to secure your app"
                forgotPinText.visibility = View.GONE
            }
            MODE_CHANGE -> {
                titleText.text = "Change PIN"
                messageText.text = "Enter your new PIN"
                forgotPinText.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        // Number buttons (0-9)
        for (i in numberButtons.indices) {
            numberButtons[i].setOnClickListener {
                if (currentPin.length < 4) {
                    // Add the number to the PIN
                    currentPin.append(i)
                    
                    // Update the PIN dots
                    updatePinDisplay()
                    
                    // Check if PIN is complete (4 digits)
                    if (currentPin.length == 4) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            processPin()
                        }, 200) // Small delay to see the last dot filled
                    }
                }
            }
        }
        
        // Clear button
        buttonClear.setOnClickListener {
            clearPin()
        }
        
        // Delete button
        buttonDelete.setOnClickListener {
            if (currentPin.isNotEmpty()) {
                currentPin.deleteCharAt(currentPin.length - 1)
                updatePinDisplay()
            }
        }
        
        // Forgot PIN text
        forgotPinText.setOnClickListener {
            showForgotPinDialog()
        }
    }

    private fun updatePinDisplay() {
        // Update the PIN dots based on the current PIN length
        for (i in pinDots.indices) {
            if (i < currentPin.length) {
                pinDots[i].setBackgroundResource(R.drawable.pin_dot_filled)
            } else {
                pinDots[i].setBackgroundResource(R.drawable.pin_dot_empty)
            }
        }
    }

    private fun processPin() {
        when (pinMode) {
            MODE_VERIFY -> verifyPin()
            MODE_SET -> setPin()
            MODE_CHANGE -> changePin()
        }
    }

    private fun verifyPin() {
        val savedPin = settingsManager.getAppPin()
        
        if (currentPin.toString() == savedPin) {
            // PIN matches, proceed to main app
            proceedToApp()
        } else {
            // PIN doesn't match
            showErrorAnimation()
            
            // Increment attempt count
            attemptCount++
            
            if (attemptCount >= MAX_ATTEMPTS) {
                // Too many failed attempts
                showTooManyAttemptsDialog()
            } else {
                // Show error message
                val attemptsLeft = MAX_ATTEMPTS - attemptCount
                messageText.text = "Incorrect PIN. $attemptsLeft attempts remaining."
                clearPin()
            }
        }
    }

    private fun setPin() {
        if (confirmPin.isEmpty()) {
            // First entry, store the PIN
            confirmPin.append(currentPin)
            currentPin.clear()
            
            // Update UI for confirmation
            messageText.text = getString(R.string.confirm_pin)
            updatePinDisplay()
        } else {
            // Second entry, confirm the PIN
            if (currentPin.toString() == confirmPin.toString()) {
                // PINs match, save the PIN
                settingsManager.setAppPin(currentPin.toString())
                settingsManager.setAppLockEnabled(true)
                
                // Show success message
                Toast.makeText(this, "PIN set successfully", Toast.LENGTH_SHORT).show()
                
                // Return to settings
                finish()
            } else {
                // PINs don't match, start over
                showErrorAnimation()
                messageText.text = getString(R.string.pin_does_not_match)
                confirmPin.clear()
                currentPin.clear()
                updatePinDisplay()
                
                // After a delay, reset to initial state
                Handler(Looper.getMainLooper()).postDelayed({
                    messageText.text = getString(R.string.enter_pin)
                }, 2000)
            }
        }
    }

    private fun changePin() {
        // This function handles changing an existing PIN
        // The process involves verifying the old PIN, then setting a new one
        
        if (currentPin.toString() == settingsManager.getAppPin()) {
            // Old PIN verified, now set a new PIN
            // Change mode to set new PIN
            pinMode = MODE_SET
            
            // Clear current PIN and update UI
            currentPin.clear()
            messageText.text = getString(R.string.enter_new_pin)
            updatePinDisplay()
        } else {
            // Incorrect old PIN
            showErrorAnimation()
            messageText.text = getString(R.string.pin_incorrect)
            currentPin.clear()
            updatePinDisplay()
        }
    }

    private fun clearPin() {
        currentPin.clear()
        updatePinDisplay()
    }

    private fun showErrorAnimation() {
        // Create a shake animation programmatically
        val shake = AnimationUtils.loadAnimation(this, R.anim.shake)
        // Use pinIndicatorContainer as the anchor for the animation
        findViewById<View>(R.id.pinIndicatorContainer).startAnimation(shake)
    }

    private fun showForgotPinDialog() {
        AlertDialog.Builder(this)
            .setTitle("Forgot PIN?")
            .setMessage("This will reset your PIN but all your recordings will remain intact. Continue?")
            .setPositiveButton("Reset PIN") { _, _ ->
                // Reset PIN
                settingsManager.setAppPin("")
                settingsManager.setAppLockEnabled(false)
                
                // Restart activity in set mode
                val intent = Intent(this, PinLockActivity::class.java)
                intent.putExtra("mode", MODE_SET)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTooManyAttemptsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Too Many Attempts")
            .setMessage("You have made too many incorrect attempts. Please try again later.")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun proceedToApp() {
        // Set flag to prevent app lock check cycle
        SecretRecorderBaseActivity.isJustUnlocked = true
        
        // Navigate to the main activity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}