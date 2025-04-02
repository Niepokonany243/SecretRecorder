# New Improvements for SecretRecorder

## Issues to Fix

1. **FPS still not being applied correctly in recordings**
   - Need to ensure frame rate settings are properly passed to Camera2 API
   - Fix potential issues in MediaRecorder configuration

2. **Create a numeric keypad lock system**
   - Replace text password with PIN-based lock
   - Implement 3x3 grid of number buttons (123, 456, 789)
   - Update settings screen to support PIN setup

## Approach

### 1. FPS Issue
1. Identify why the current FPS settings aren't being applied
2. Check camera configuration parameters
3. Add more robust FPS setting in MediaRecorder
4. Test recording with different FPS settings

### 2. Numeric Keypad Lock
1. Create a new layout for PIN entry
2. Create logic for PIN verification
3. Update the settings to allow PIN setup
4. Connect the PIN lock to app startup flow 