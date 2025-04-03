# Secret Recorder

A sophisticated Android application for discreet audio and video recording with advanced features and a modern user interface.

## Features

### Recording Capabilities
- **Flexible Recording Modes**
  - Audio-only recording (.m4a format)
  - Video recording with audio (.mp4 format)
  - Front/back camera support
  - Flash control for low-light recording

- **Quality Options**
  - Multiple video quality presets (480p, 720p, 1080p, 4K UHD)
  - Adjustable frame rates (30fps, 60fps)
  - Optimized bitrate settings for quality and storage balance
  - Automatic quality detection based on device capabilities

- **Background Operation**
  - Reliable background recording with foreground service
  - Battery optimization bypassing for uninterrupted recording
  - Auto-restart on device reboot
  - Automatic recording segmentation (30-minute intervals)

### Media Management
- **Built-in Media Players**
  - Custom video player with zoom and pan capabilities
  - Dedicated audio player with playback controls
  - Seek forward/backward functionality
  - Time display and progress tracking

- **Recording Browser**
  - Organized gallery of all recordings
  - Thumbnail previews for videos
  - Detailed metadata display (resolution, duration, date)
  - Search and filter capabilities
  - Tab-based organization (All/Video/Audio)

- **Storage Management**
  - Automatic file organization
  - Built-in storage space management
  - Automatic cleanup of old recordings when space is low
  - Secure file sharing support

### Security Features
- **PIN Lock Protection**
  - Numeric keypad for secure access
  - Configurable PIN code
  - Attempt limiting for brute force protection
  - Auto-lock after inactivity

- **Privacy Protection**
  - Optional file encryption
  - Secure file paths
  - Permission management
  - FileProvider for secure file sharing

## Technical Details
- **Minimum SDK**: 28 (Android 9.0)
- **Target SDK**: 34 (Android 14)
- **Package**: `secret.recorder`
- **Version**: 1.0

## System Requirements
### Permissions
- Camera access
- Audio recording
- Background service operation
- Wake lock capability
- Boot completion events
- Storage access

### Hardware
- Camera with autofocus support
- Audio recording capability
- Sufficient storage space (recommended 1GB+)

## Implementation Notes
- Uses Camera2 API for advanced video capture
- Implements foreground services for reliable background operation
- WorkManager for scheduled maintenance tasks
- FileProvider for secure file sharing
- Wake lock management for continuous operation
- Retry mechanism for camera errors
- Automatic recording segmentation for file size management
- Custom UI components for enhanced user experience

## Security Architecture
- AES-256 encryption for sensitive files
- Secure PIN-based authentication
- Privacy-focused design
- Minimal data collection
- No network dependencies
- Secure file handling

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
    ├── video_YYYYMMDD_HHMMSS_WIDTHxHEIGHT_FPSfps.mp4
    └── audio_YYYYMMDD_HHMMSS.m4a
```

## User Interface
- Material Design 3 components
- Intuitive recording controls
- Modern media browser
- Advanced video player with gesture controls
- Dedicated audio player
- Settings panel for customization
- PIN lock screen for security

## Performance Optimizations
- Hardware acceleration for smooth video playback
- Efficient thumbnail caching
- Optimized camera configuration
- Battery-efficient background operation
- Memory management for large media files
- LRU cache implementation for thumbnails

## Developer Notes
- Comprehensive error handling
- Detailed logging for troubleshooting
- Battery optimization documentation
- Automatic cleanup mechanisms
- Proguard rules for release builds

## License
This project is proprietary software. All rights reserved.

---

© 2023 Secret Recorder. All rights reserved.