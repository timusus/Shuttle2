---
name: run-android
description: Build the debug APK, install it on a connected device/emulator via ADB, and launch the app.
user_invocable: true
---

# Run Android

Build, install, and launch the debug app. Pick the device in this order and say which one you used.

## 1. A physical device, if one is attached

A device listed as `device` (not `unauthorized`/`offline`) in `adb devices` beats every emulator.

```bash
build-brief ./gradlew :android:app:installDebug && adb shell am start -n com.simplecityapps.shuttle.dev/com.simplecityapps.shuttle.ui.MainActivity
```

## 2. A lane on the WSL desktop box (the default when no device is attached)

`support/scripts/remote-emu.sh status`, then `start` and `eval "$(support/scripts/remote-emu.sh env)"`
— lane protocol, ports and stop discipline are in `.claude/rules/android.md`'s Desktop Emulator section.

```bash
support/scripts/remote-emu.sh install   # runs :android:app:assembleDebug and installs over the tunnel
adb shell am start -n com.simplecityapps.shuttle.dev/com.simplecityapps.shuttle.ui.MainActivity
```

Use `remote-emu.sh install` instead of `:android:app:installDebug` (Gradle's install task ignores
`ANDROID_ADB_SERVER_PORT`). Shell state does not persist between tool calls, so re-run the `eval`
in each command that uses `adb`. **`remote-emu.sh stop` when done**, including after a failure.

## 3. A local AVD, only if `status` says the box is unreachable or all three lanes are busy

```bash
build-brief ./gradlew :android:app:installDebug && adb shell am start -n com.simplecityapps.shuttle.dev/com.simplecityapps.shuttle.ui.MainActivity
```

Kill it afterwards; never leave a local AVD running, and never kill one you did not start.

If no device is connected and the box is unreachable, check with `adb devices` and report the issue.
