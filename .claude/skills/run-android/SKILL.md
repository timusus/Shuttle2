---
name: run-android
description: Build the debug APK, install it on a connected device/emulator via ADB, and launch the app.
user_invocable: true
---

# Run Android

Build, install, and launch the debug app on a connected device.

```bash
./gradlew :android:app:assembleDebug && ./gradlew :android:app:installDebug && adb shell am start -n com.simplecityapps.shuttle.dev/com.simplecityapps.shuttle.ui.MainActivity
```

If no device is connected, check with `adb devices` and report the issue.
