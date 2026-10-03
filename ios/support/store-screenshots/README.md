# App Store screenshots (iOS)

Capture and framing of the Shuttle Music App Store screenshots, ported from Shuttle Podcasts. `capture.sh`
signs in to the Jellyfin test server (`~/.config/s2-test/jellyfin.env`, as `ios/scripts/maestro-sim.sh` does)
on a leased iPhone simulator and the project's `S2 iPad`, then walks the slots in `slots.json`;
`render.py` frames the raw captures with a headline for each App Store canvas.

Nothing here ships: the screenshot hooks (`ios/S2/Debug/ScreenshotHooks.swift`, called from `ContentView`)
are inside `#if DEBUG`, so a Release build has neither the code nor the call.

## Run

```sh
ios/support/store-screenshots/capture.sh            # build, install, sign in, capture iPhone and iPad -> raw/
ios/support/store-screenshots/render.py             # frame raw/ -> ios/store/screenshots/en-AU/<canvas>/<n>.png
```

Options: `capture.sh --skip-build` (install the last Debug build), `--skip-setup` (keep the signed-in state),
`--real-artwork` (capture the library's real covers instead of generated artwork), `--device iphone|ipad`;
`render.py --canvas iphone-6.9 --slot 3`. Canvases: `iphone-6.9` (1320x2868),
`iphone-6.5` (1284x2778), both from the iPhone 16's raw captures, and `ipad-13` (2064x2752) from the iPad.
Needs Maestro, xcodegen, Google Chrome (headless framing; the DM Sans headline font loads from Google Fonts,
and the last line of `render.py` output says which font rendered) and the test server being reachable.

## Slots

`slots.json`: `steps` are `hook:<action>` (written to `Documents/screenshot_hook.url`, polled by the Debug build;
actions in `ScreenshotHooks.swift`: `tab`, `library`, `route`, `settings`, `player`, `reset`),
`maestro:<flow>` (`maestro/`, for what a hook can't reach: playing a song, the first album, the queue) and
`sleep:<seconds>`. Seven slots: Sources, Home, Albums, Album detail, Now Playing, Queue, Equalizer. The headlines
only claim what iOS has today.

## Artwork

The test server's library is commercial music (Metallica, Tool, Pink Floyd and so on), so real covers in
the frames would be a guideline 5.2 risk. `capture.sh` therefore switches the app to generated artwork
before capturing (`S2GeneratedArtwork` in the app's defaults, written before launch; until that setting
ships the default is harmless and the real covers still show). `--real-artwork` captures the library's
real covers instead — do not upload those frames as they are. `raw/` and `ios/store/screenshots/` are
git-ignored either way.
