# App Store screenshots (iOS)

Capture and framing of the Shuttle Music App Store screenshots, ported from Shuttle Podcasts. `capture.sh`
installs the Debug app on a leased iPhone simulator and the project's `S2 iPad`, gives it an invented library of
local files, a listening history and a part-played album, then walks the slots in `slots.json`; `render.py` frames
the raw captures with a headline for each App Store canvas. No server, account or network is involved.

Nothing here ships: the screenshot hooks (`ios/S2/Debug/ScreenshotHooks.swift`, called from `ContentView`)
are inside `#if DEBUG`, so a Release build has neither the code nor the call.

## Run

```sh
ios/support/store-screenshots/capture.sh            # build, install, set up, capture iPhone and iPad -> raw/
ios/support/store-screenshots/render.py             # frame raw/ -> ios/store/screenshots/en-AU/<canvas>/<n>.png
ios/support/store-screenshots/contact-sheet.py ios/store/screenshots/en-AU/ipad-13 /tmp/ipad.png   # one-row review
```

Options: `capture.sh --skip-build` (install the last Debug build), `--skip-setup` (keep the installed app, its
library, history and paused album), `--device iphone|ipad`, `--paywall-only` (iPhone, the paywall alone; `render.py --paywall-only` then writes it); `render.py --canvas iphone-6.9 --slot 3`. Canvases:
`iphone-6.9` (1320x2868), `iphone-6.5` (1284x2778), both from the iPhone 16's raw captures, and `ipad-13`
(2064x2752) from the iPad. Needs Maestro, xcodegen, ffmpeg, Pillow and Google Chrome (headless framing; the DM Sans
headline font loads from Google Fonts, and the last line of `render.py` output says which font rendered).

## Setup

For each device, before the slots, `capture.sh`:

1. Reinstalls the app, and clears the debug switches that live in the simulator's defaults and outlive an uninstall
   (generated artwork, the entitlement override).
2. Builds the library (`build-library.py`, below) and copies it into the app's Documents folder, then walks the
   first-run setup with "Use this device" (`maestro/onboard-local.yaml`), which imports it.
3. Seeds a listening history (`seed-history.py <song.db>`: completed plays of six albums, 29 to 42 days back), so Home
   opens on Jump Back In rather than its first-run hint. The plays sit outside Heavy Rotation's 28-day window: its
   artist circles get their pictures from the S2 artwork API by name, which has none for an invented artist.
4. Turns the equalizer on with the Bass Boost preset (`equalizer_enabled` and `preset_name` in the app's defaults).
5. Plays Undertow and pauses it about 20 s in (`maestro/play-album.yaml`), so the mini player, Now Playing and the
   queue have a song part-way through.

## Library

`build-library.py` turns the sample library manifest (`android/fixtures/src/main/resources/sample-library/
library.json`: 16 albums and 97 songs by invented artists, with generated covers in `covers/`) into
`library/<artist>/<album>/<nn> <title>.flac`: silent 16-bit FLAC at each song's length, fully tagged, with the
album's cover embedded. It's a few MB, rebuilt only where a file is missing (delete `library/` after the manifest
changes), and git-ignored, as are `raw/` and `ios/store/screenshots/`.

## Slots

`slots.json`: `steps` are `hook:<action>` (written to `Documents/screenshot_hook.url`, polled by the Debug build;
actions in `ScreenshotHooks.swift`: `tab`, `library`, `route`, `settings`, `player`, `paywall`, `miniplayer`, `reset`),
`maestro:<flow>` (`maestro/`, for what a hook can't reach: the album, the queue, the paywall), `entitlement:<name>`
(the debug entitlement override, applied by relaunching) and `sleep:<seconds>`. A slot's `ipad_steps`, when present,
replace its `steps` on the iPad: there the Equalizer is pushed in Library rather than shown in the
Settings sheet, and the player opens full screen (`player?fullScreen=1`) rather than as a form sheet, so every frame
fills the screen. The iPhone's Equalizer slot hides the mini player (`miniplayer?hidden=1`), which would cover the bands, and scrolls them into view (`eq-bands.yaml`). Six slots: Albums (under the server headline), Home, Album detail, Now Playing, Queue, Equalizer. The headlines only
claim what iOS has today.

`paywall` is App Store Connect's in-app purchase review screenshot: the Pro paywall as a free user, captured on the
iPhone after the slots and written by `render.py` unframed, scaled to the 6.9" canvas, to
`ios/store/screenshots/en-AU/iap-review/paywall.png`. StoreKit's test configuration (`S2.storekit`) only applies
when Xcode runs the app, so the capture's `paywall?price=$14.99` hook gives `StoreKitManager` a stand-in price, which
the paywall shows as it does once the products load.

## Artwork

Every cover comes from the library's own files, as a user's would. The earlier captures signed in to the Jellyfin
test server (commercial music, a guideline 5.2 risk) and switched on the debug "Use generated artwork" setting,
whose tiles are a muted tone and a symbol, which reads as missing artwork; that setting also stayed on in the
simulator's defaults across reinstalls, which is why later captures showed placeholders throughout. The invented
library replaces both.
