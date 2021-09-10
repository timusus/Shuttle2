#!/usr/bin/env bash

cp "$GOOGLE_SERVICES" ./androidApp/main/app/google-services.json

chmod +x ./gradlew

./gradlew :androidApp:main:publishReleaseBundle --parallel -PkeyAlias=shuttle -PkeyPass="$KEYSTORE_PASS" -PstorePass="$KEYSTORE_PASS"