#!/usr/bin/env bash

PAYLOAD=$(
  cat ./app/src/main/assets/changelog.json |
    jq '{ "embeds" : [ { title: "S2 Music Player \(.[0].versionName) Released", description: "Releases usually take 4-16 hours to arrive in the alpha channel and are moved to beta once deemed stable.

**Changelog**\n\n\( [if .[0].features | length != 0 then "**Features**\n• \(.[0].features| join("\n• "))\n" else empty end, if .[0].improvements | length != 0 then "**Improvements**\n• \(.[0].improvements | join("\n• "))\n" else empty end, if .[0].fixes | length != 0 then "**Fixes**\n• \(.[0].fixes | join("\n• "))\n" else empty end ] | join ("\n") )" } ] }'
)

curl -X POST -H "Content-Type: application/json" -d "$PAYLOAD" "$1"