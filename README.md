# FocusGuard

An offline Android digital detox app that helps users stay focused by locking their phone for a specific duration.

## Features

- 🔒 **Smart Lock Mechanism**: Full-screen overlay that blocks touch while allowing power button functionality
- 🔋 **Battery Optimized**: Pauses timer updates when screen is off to save battery
- 📞 **Call-Friendly**: Automatically hides overlay during phone calls
- ⚡ **Offline-First**: No backend required, works entirely offline
- 🔐 **Safety Mechanism**: Lock won't reactivate after device restart

## Technical Highlights

- Built with Kotlin and Jetpack Compose
- Foreground Service with WindowManager overlay
- Screen state monitoring for battery optimization
- Phone state listener for call handling
- Minimum SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)

## Permissions Required

- `SYSTEM_ALERT_WINDOW` - For overlay window
- `FOREGROUND_SERVICE` - For persistent lock service
- `READ_PHONE_STATE` - For call detection
- Battery optimization exemption (requested at runtime)

## Building

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

## CI/CD

The project includes a GitHub Actions workflow that automatically builds a Debug APK on every push to the main branch. Download the APK from the Actions tab.

## Safety Features

- Service returns `START_NOT_STICKY` to prevent auto-restart after reboot
- No `BOOT_COMPLETED` receiver to prevent permanent device locking
- Power button always works to turn screen on/off
- Call handling ensures users can answer incoming calls

## How It Works

1. User selects focus duration (hours and minutes)
2. App requests necessary permissions
3. Foreground service starts with full-screen overlay
4. Timer counts down, pausing when screen is off
5. Overlay temporarily hides during phone calls
6. Lock automatically releases when timer expires

## License

MIT License - Feel free to use and modify as needed.
