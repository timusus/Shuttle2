#!/usr/bin/env bash

cp "$GOOGLE_SERVICES" ./app/google-services.json

chmod +x ./gradlew

./gradlew publishReleaseBundle --parallel -PkeyAlias=shuttle -PkeyPass="$KEYSTORE_PASS" -PstorePass="$KEYSTORE_PASS"