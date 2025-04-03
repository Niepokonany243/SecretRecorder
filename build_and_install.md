# Build and Install Instructions

To build and install the app with the privacy indicator feature:

1. Open Android Studio
2. Open the project at `c:\Users\Krystian\AndroidStudioProjects\SecretRecorder`
3. Click on "Build" > "Make Project" to build the app
4. Connect your Android device (Android 12+) to your computer
5. Click on "Run" > "Run 'app'" to install and run the app on your device

## Testing the Privacy Indicator Feature

1. Launch the app
2. Start recording (either audio or video)
3. If you're using an Android 12+ device, you should see the privacy indicator dialog
4. Try both options:
   - Use the overlay (requires SYSTEM_ALERT_WINDOW permission)
   - Disable with ADB (requires LADB or ADB connection)
5. Test the "Don't show again" checkbox

## Troubleshooting

If you encounter any issues:

1. Make sure you're using an Android 12+ device for testing
2. Check that the SYSTEM_ALERT_WINDOW permission is granted for the overlay option
3. For the ADB option, you'll need to have LADB installed or connect to ADB from your computer

## Implementation Details

The privacy indicator feature consists of:

1. `PrivacyIndicatorManager.kt` - Manages the privacy indicator functionality
2. `PrivacyIndicatorOverlayService.kt` - Displays the overlay
3. Layout files for the dialog and overlay
4. Updates to existing classes to integrate the feature

The feature works in both foreground and background recording flows, and it's only activated on Android 12+ devices.

## Fixed Issues

- Fixed an issue where showing a dialog from a service context would crash the app
- Now the app checks if the context is an Activity before showing the dialog
- If called from a service, it will automatically enable the overlay if permission is granted
