---
name: run-android
description: Build the debug APK, install it on a connected device/emulator via ADB, and launch the app.
user_invocable: true
---

# Run Android

Build, install, and launch the debug app. Prefer, in order: a physical device already listed in
`adb devices`; a lane on the WSL desktop box (`support/scripts/remote-emu.sh status`, then `start`
and `eval "$(support/scripts/remote-emu.sh env)"` — see the CLAUDE.md "Desktop Emulator" section);
a local AVD only if the box is unreachable or all three lanes are busy.

```bash
./gradlew :android:app:assembleDebug && ./gradlew :android:app:installDebug && adb shell am start -n com.simplecityapps.shuttle.dev/com.simplecityapps.shuttle.ui.MainActivity
```

On a box lane, use `support/scripts/remote-emu.sh install` instead of `:android:app:installDebug`
(Gradle's install task ignores `ANDROID_ADB_SERVER_PORT`), then `adb shell am start ...` as above —
remember to `eval` `env` first, and `remote-emu.sh stop` when done.

If no device is connected, check with `adb devices` and report the issue.
