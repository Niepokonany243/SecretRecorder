package com.example.systemservice

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi

/**
 * Manager class for handling privacy indicator functionality
 * This class manages the privacy indicator dialog and overlay for Android 12+ devices
 */
class PrivacyIndicatorManager(private val context: Context) {

    companion object {
        private const val TAG = "PrivacyIndicatorManager"

        // Command to disable privacy indicators using ADB
        const val DISABLE_INDICATOR_COMMAND = "cmd device_config put privacy camera_mic_icons_enabled false"

        /**
         * Check if the device is running Android 12 (API 31) or higher
         */
        fun isAndroid12OrHigher(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }
    }

    private val settingsManager = AppSettingsManager(context)

    /**
     * Show the privacy indicator dialog if needed
     * @return true if dialog was shown, false otherwise
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun showPrivacyIndicatorDialogIfNeeded(): Boolean {
        // Skip if "don't show again" is enabled or if not on Android 12+
        if (settingsManager.shouldHidePrivacyIndicatorDialog() || !isAndroid12OrHigher()) {
            return false
        }

        // Check if context is an Activity before showing dialog
        if (context is android.app.Activity) {
            showPrivacyIndicatorDialog()
            return true
        } else {
            // If called from a service, we can't show a dialog
            Log.d(TAG, "Cannot show dialog from non-activity context")
            return false
        }
    }

    /**
     * Show the privacy indicator dialog
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun showPrivacyIndicatorDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.privacy_indicator_dialog, null)
        val dontShowAgainCheckbox = dialogView.findViewById<CheckBox>(R.id.dontShowAgainCheckbox)
        val commandTextView = dialogView.findViewById<TextView>(R.id.adbCommandText)
        val copyButton = dialogView.findViewById<Button>(R.id.copyCommandButton)

        // Set the command text
        commandTextView.text = DISABLE_INDICATOR_COMMAND

        // Set up copy button
        copyButton.setOnClickListener {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("ADB Command", DISABLE_INDICATOR_COMMAND)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(context, R.string.command_copied, Toast.LENGTH_SHORT).show()
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.privacy_indicator_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Save "don't show again" preference if checked
                if (dontShowAgainCheckbox.isChecked) {
                    settingsManager.setShouldHidePrivacyIndicatorDialog(true)
                }
            }
            .setCancelable(true)
            .create()

        dialog.show()
    }

    /**
     * Show instructions for disabling privacy indicators with ADB
     */
    private fun showAdbInstructions() {
        // This method is no longer used as we show instructions directly in the dialog
        // but we keep it for potential future use
        AlertDialog.Builder(context)
            .setTitle(R.string.adb_instructions_title)
            .setMessage(R.string.adb_instructions_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
