# Open Recorder

An open-source screen recorder for Android with internal audio and a modern interface. No ads, no trackers and no internet access.

## Features

### Recording

- Record your screen to MP4 files saved in `Movies/Open Recorder`
- **Audio sources:** no audio, internal audio, microphone, or microphone and internal audio mixed together
- **Audio sample rate:** 44.1 kHz or 48 kHz
- **Video codec:** H.264 or H.265
- **Resolution:** native, 1080p, 720p, or 480p
- **Frame rate:** automatic, 120, 90, 60, or 30 fps
- **Video bitrate:** automatic, 4, 8, 16, or 24 Mbps
- **Orientation:** automatic, portrait, or landscape
- **Force 16:9 with letterboxing:** fits the full capture inside a 16:9 frame without cropping
- **Recording countdown:** off, 3, 5, or 10 seconds, so you can open the screen you want to capture. You can cancel while it counts down
- **File naming pattern:** `dd-mm-yyyy`, `mm-dd-yyyy`, `yyyy-mm-dd`, or `yyyy-dd-mm`, followed by `_hh-mm-ss`

### Controls

- Start and stop from the app's main screen
- **Notification controls:** pause, resume, and stop while recording
- **Quick Settings tile:** tap to start, and tap again to stop. It also shows the current state (starting, recording, paused, saving, stopping)
- Recording keeps running if you close the app
- After saving, a notification lets you **watch**, **share**, or **delete** the video

### Recordings tab

- Lists the videos recorded by Open Recorder, with total count
- Sort by date, size, or duration
- Open a video in your default player
- Select one, several, or all recordings and delete them (Android asks you to confirm)

### Appearance and settings

- Light, dark, or follow-system themes
- Dynamic color (Monet) variants of each theme
- Optional predictive back gesture (off by default)
- Settings are saved between sessions

### About

- Project page with a link to the source code and the GPL-3.0 license
- Built-in list of open-source licenses used by the app

## Privacy

- **No internet permission.** The app cannot connect to the network
- No ads, no analytics, no crash reporting, no tracking
- Backups are disabled
- Recordings stay on your device
- Other apps are not allowed to capture Open Recorder's audio

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Microphone and internal audio capture |
| `POST_NOTIFICATIONS` | Recording controls and "saved" notification |
| `FOREGROUND_SERVICE` | Keeps recording while the app is in the background |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Required by Android for screen capture services |
| `FOREGROUND_SERVICE_MICROPHONE` | Required by Android for microphone capture services |

## Requirements

Android 10 (API 29) or later.

## Limitations

These come from Android itself, not from this app:

- Android asks for your confirmation every time you start a recording.
- Apps that block capture (DRM video, banking apps, anything using `FLAG_SECURE`) can appear black or silent.
- Internal audio only includes apps that allow playback capture.

## Credits

- Capture engine inspired by the Android Open Source Project (AOSP) SystemUI screen recorder
- UI built with [Miuix](https://github.com/compose-miuix-ui/miuix) for Compose
- Dynamic colors powered by [MaterialKolor](https://github.com/jordond/materialkolor)
- Navigation and UI are adapted from [AsteriskNG](https://github.com/Asterisk4Magisk/AsteriskNG) (Copyright 2026, AsteriskNG contributors), licensed under GPL-3.0.
See [NOTICE] (NOTICE) for full third-party attributions.

## License

[GPL-3.0-only](LICENSE)
