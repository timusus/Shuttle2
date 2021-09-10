#!/usr/bin/env bash

cp "$GOOGLE_SERVICES" ./androidApp/main/app/google-services.json

"$ANDROID_SDK_ROOT/emulator/emulator" -avd android-30 -no-window -no-audio &

WAIT_CMD="$ANDROID_SDK_ROOT/platform-tools/adb wait-for-device shell getprop init.svc.bootanim"
until $WAIT_CMD | grep -m 1 stopped; do
  echo "Waiting..."
  sleep 1
done

"$ANDROID_SDK_ROOT/platform-tools/adb" uninstall com.simplecityapps.shuttle.dev

chmod +x ./gradlew
./gradlew clean :androidApp:main:app:connectedAndroidTest -PkeyAlias=shuttle -PkeyPass="$KEYSTORE_PASS" -PstorePass="$KEYSTORE_PASS"
GRADLE_RETURN_CODE=$?
echo "gradle exit code $GRADLE_RETURN_CODE"

# Kill emulator process
"$ANDROID_SDK_ROOT/platform-tools/adb" emu kill

if [ $GRADLE_RETURN_CODE -ne 0 ]; then
  echo "instrumented test failed, exiting..."
  exit 1
fi