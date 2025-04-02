package com.example.systemservice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.util.Log
import android.text.TextWatcher
import android.widget.Switch
import android.text.Editable
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : SecretRecorderBaseActivity() {

    companion object {
        // SharedPreferences constants
        const val PREFS_NAME = "SecretRecorderPrefs"
        const val KEY_VIDEO_QUALITY = "video_quality"  // 0=low, 1=medium, 2=high
        const val KEY_VIDEO_FPS = "video_fps"  // Actual FPS value
        const val KEY_APP_PASSWORD = "app_password"
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"

        // Video quality presets (matching MainActivity)
        val VIDEO_QUALITY_PRESETS = mapOf(
            "Low (480p)" to Pair(854, 480),
            "Medium (720p)" to Pair(1280, 720),
            "High (1080p)" to Pair(1920, 1080),
            "Ultra (4K)" to Pair(3840, 2160)
        )

        // Video FPS presets
        val VIDEO_FPS_PRESETS = listOf(24, 30, 60, 120)

        // Request codes
        const val REQUEST_PASSWORD_SETUP = 100
    }

    private lateinit var settingsManager: AppSettingsManager

    // UI Elements
    private lateinit var qualityLowButton: RadioButton
    private lateinit var qualityMediumButton: RadioButton
    private lateinit var qualityHighButton: RadioButton
    private lateinit var qualityUltraButton: RadioButton
    private lateinit var fpsSeekBar: SeekBar
    private lateinit var fpsValueText: TextView
    private lateinit var passwordLockSwitch: SwitchCompat
    private lateinit var changePasswordButton: Button

    private lateinit var encryptionSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var storageLocationEditText: EditText
    private lateinit var audioOnlySwitch: com.google.android.material.switchmaterial.SwitchMaterial

    // Theme UI Elements
    private lateinit var themeLightButton: RadioButton
    private lateinit var themeDarkButton: RadioButton
    private lateinit var themeSystemButton: RadioButton
    private lateinit var themeRadioGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize settings manager
        settingsManager = AppSettingsManager(this)

        // Set up the back navigation in the action bar
        setupActionBar()
        setupBackNavigation()

        // Initialize UI components
        initializeUI()

        // Load settings
        loadSettings()

        // Setup click listeners
        setupClickListeners()
    }

    override fun finish() {
        super.finish()
        // Add smooth animation when going back to previous screen
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun initializeUI() {
        // Video quality views
        qualityLowButton = findViewById(R.id.qualityLowButton)
        qualityMediumButton = findViewById(R.id.qualityMediumButton)
        qualityHighButton = findViewById(R.id.qualityHighButton)
        qualityUltraButton = findViewById(R.id.qualityUltraButton)

        // FPS views
        fpsSeekBar = findViewById(R.id.fpsSeekBar)
        fpsValueText = findViewById(R.id.fpsValueText)

        // Password lock views
        passwordLockSwitch = findViewById(R.id.passwordLockSwitch)
        changePasswordButton = findViewById(R.id.changePasswordButton)



        // Encryption switch
        encryptionSwitch = findViewById(R.id.videoEncryptionSwitch)

        // Storage location EditText
        storageLocationEditText = findViewById(R.id.storageLocationEditText)

        // Audio-only switch
        audioOnlySwitch = findViewById(R.id.audioOnlySwitch)

        // Theme UI elements
        themeLightButton = findViewById(R.id.themeLightButton)
        themeDarkButton = findViewById(R.id.themeDarkButton)
        themeSystemButton = findViewById(R.id.themeSystemButton)
        themeRadioGroup = findViewById(R.id.themeRadioGroup)

        // Set up FPS SeekBar
        fpsSeekBar.max = VIDEO_FPS_PRESETS.size - 1
    }

    private fun loadSettings() {
        // Load video quality setting
        when (settingsManager.getVideoQuality()) {
            "480p" -> qualityLowButton.isChecked = true
            "720p" -> qualityMediumButton.isChecked = true
            "1080p" -> qualityHighButton.isChecked = true
            "2160p" -> qualityUltraButton.isChecked = true
        }

        // Load FPS setting
        val fps = settingsManager.getVideoFps()
        val fpsIndex = when (fps) {
            24 -> 0
            30 -> 1
            60 -> 2
            120 -> 3
            else -> 1 // Default to 30fps
        }
        fpsSeekBar.progress = fpsIndex
        fpsValueText.text = "${VIDEO_FPS_PRESETS[fpsIndex]} FPS"

        // Load password lock setting
        passwordLockSwitch.isChecked = settingsManager.isAppLockEnabled()

        // Only show change PIN button if PIN is actually set
        changePasswordButton.visibility = if (passwordLockSwitch.isChecked && settingsManager.getAppPin().isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }

        // Update button text to use PIN terminology
        changePasswordButton.text = "Change PIN"



        // Load encryption setting
        encryptionSwitch.isChecked = settingsManager.isVideoEncryptionEnabled()

        // Load storage location
        storageLocationEditText.setText(settingsManager.getStorageLocation())

        // Load audio-only setting
        audioOnlySwitch.isChecked = settingsManager.isAudioOnly()

        // Load theme setting
        when (settingsManager.getAppTheme()) {
            "light" -> themeLightButton.isChecked = true
            "dark" -> themeDarkButton.isChecked = true
            "system" -> themeSystemButton.isChecked = true
        }
    }

    private fun setupClickListeners() {
        // FPS SeekBar listener
        fpsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                fpsValueText.text = "${VIDEO_FPS_PRESETS[progress]} FPS"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val fps = VIDEO_FPS_PRESETS[seekBar.progress]
                Log.d("SettingsActivity", "Saving FPS setting: $fps")
                settingsManager.setVideoFps(fps)
                settingsManager.logAllSettings() // Log all settings after change
            }
        })

        // Video quality radio group listener
        val qualityRadioGroup = findViewById<RadioGroup>(R.id.qualityRadioGroup)
        qualityRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val quality = when (checkedId) {
                R.id.qualityLowButton -> "480p"
                R.id.qualityMediumButton -> "720p"
                R.id.qualityHighButton -> "1080p"
                R.id.qualityUltraButton -> "2160p"
                else -> "720p"
            }
            Log.d("SettingsActivity", "Saving video quality setting: $quality")
            settingsManager.setVideoQuality(quality)
            settingsManager.logAllSettings() // Log all settings after change
        }

        // Password lock switch listener
        passwordLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (settingsManager.getAppPin().isEmpty()) {
                    // Launch PIN setup
                    val intent = Intent(this, PinLockActivity::class.java)
                    intent.putExtra("mode", PinLockActivity.MODE_SET)
                    startActivity(intent)
                } else {
                    // PIN already exists, show change button
                    changePasswordButton.visibility = View.VISIBLE
                }
            } else {
                // PIN lock disabled, hide change button
                changePasswordButton.visibility = View.GONE
                settingsManager.setAppLockEnabled(false)
            }
        }

        // Change password button listener
        changePasswordButton.setOnClickListener {
            // Launch PIN change
            val intent = Intent(this, PinLockActivity::class.java)
            intent.putExtra("mode", PinLockActivity.MODE_CHANGE)
            startActivity(intent)
        }



        // Encryption switch listener
        encryptionSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setVideoEncryptionEnabled(isChecked)
        }

        // Storage location text watcher
        storageLocationEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                settingsManager.setStorageLocation(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Theme radio group listener
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.themeLightButton -> "light"
                R.id.themeDarkButton -> "dark"
                R.id.themeSystemButton -> "system"
                else -> "system"
            }
            Log.d("SettingsActivity", "Saving theme setting: $theme")
            settingsManager.setAppTheme(theme)

            // Apply the theme immediately
            applyTheme(theme)
        }

        // Audio-only switch listener
        audioOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setAudioOnly(isChecked)
        }
    }

    private fun showPasswordDialog() {
        // Launch PIN setup (new method replaces old dialog)
        val intent = Intent(this, PinLockActivity::class.java)
        intent.putExtra("mode", PinLockActivity.MODE_SET)
        startActivity(intent)
    }

    /**
     * Apply the selected theme immediately
     */
    private fun applyTheme(theme: String) {
        when (theme) {
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


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()

        // Update change PIN button visibility based on current state
        changePasswordButton.visibility = if (passwordLockSwitch.isChecked && settingsManager.getAppPin().isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}