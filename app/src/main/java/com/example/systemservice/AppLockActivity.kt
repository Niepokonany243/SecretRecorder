package com.example.systemservice

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AppLockActivity : AppCompatActivity() {

    private lateinit var passwordEditText: EditText
    private lateinit var unlockButton: Button
    private lateinit var errorText: TextView
    private lateinit var settingsManager: AppSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock)

        // Initialize views
        passwordEditText = findViewById(R.id.passwordEditText)
        unlockButton = findViewById(R.id.unlockButton)
        errorText = findViewById(R.id.errorText)

        // Initialize settings manager
        settingsManager = AppSettingsManager(this)
        
        // Setup unlock button
        unlockButton.setOnClickListener {
            val enteredPassword = passwordEditText.text.toString()
            val storedPassword = settingsManager.getAppPassword()
            
            if (enteredPassword == storedPassword) {
                // Password correct, proceed to main activity
                // Set flag to prevent app lock check cycle
                SecretRecorderBaseActivity.isJustUnlocked = true
                
                val mainIntent = Intent(this, MainActivity::class.java)
                startActivity(mainIntent)
                finish()
            } else {
                // Incorrect password
                errorText.text = "Incorrect password. Try again."
                passwordEditText.text.clear()
                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        // Prevent going back; close the app instead
        finishAffinity()
    }
} 