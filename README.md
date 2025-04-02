# Secret Recorder

A sophisticated Android application for discreet audio and video recording with advanced features.

## Features
- **Flexible Recording Modes**
  - Audio-only recording (.m4a)
  - Video recording with audio (.mp4)
  - Front/back camera support
  - Flash control support
  
- **Quality Options**
  - Configurable video quality presets
  - Support for 720p HD recording
  - Optimized bitrate settings
  - Adjustable frame rates

- **Background Operation**
  - Reliable background recording
  - Battery optimization bypassing
  - Auto-restart on device reboot
  - Automatic recording management

- **Storage Management**
  - Automatic file organization
  - Built-in storage space management
  - File encryption capabilities
  - Secure file sharing support

- **Recording Browser**
  - Built-in media player
  - Recording details viewer
  - Share functionality
  - File management options

## Technical Details
- Minimum SDK: 28 (Android 9.0)
- Target SDK: 34 (Android 14)
- Package: `secret.recorder`
- Version: 1.0

## System Requirements
### Permissions
- Camera access
- Audio recording
- Background service operation
- Wake lock capability
- Boot completion events

### Hardware
- Camera with autofocus support
- Audio recording capability
- Sufficient storage space

## Implementation Notes
- Uses Camera2 API for video capture
- Implements foreground services for reliability
- WorkManager for scheduled tasks
- FileProvider for secure file sharing
- Wake lock management for continuous operation
- Retry mechanism for camera errors
- Automatic recording segmentation (30-minute intervals)

## Security Features
- File path security
- Storage encryption support
- Permission management
- Secure file sharing
- Battery optimization bypassing

## Build Configuration
```gradle
applicationId = "secret.recorder"
minSdk = 28
targetSdk = 34
versionCode = 1
versionName = "1.0"
```

## Storage Structure
```
External Storage/
└── Recordings/
    ├── video_YYYYMMDD_HHMMSS.mp4
    └── audio_YYYYMMDD_HHMMSS.m4a
```

## Developer Notes
- Proguard rules implemented for release builds
- Comprehensive error handling
- Battery optimization documentation included
- Automatic cleanup mechanisms

