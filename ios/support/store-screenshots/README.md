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
`--device iphone|ipad`; `render.py --canvas iphone-6.9 --slot 3`. Canvases: `iphone-6.9` (1320x2868),
`iphone-6.5` (1284x2778), both from the iPhone 16's raw captures, and `ipad-13` (2064x2752) from the iPad.
Needs Maestro, xcodegen, Google Chrome (headless framing; the DM Sans headline font loads from Google Fonts,
and the last line of `render.py` output says which font rendered) and the test server being reachable.

## Slots

`slots.json`: `steps` are `hook:<action>` (written to `Documents/screenshot_hook.url`, polled by the Debug build;
actions in `ScreenshotHooks.swift`: `tab`, `library`, `route`, `settings`, `player`, `reset`),
`maestro:<flow>` (`maestro/`, for what a hook can't reach: playing a song, the first album, the queue) and
`sleep:<seconds>`. Seven slots: Sources, Home, Albums, Album detail, Now Playing, Queue, Equalizer. The headlines
only claim what iOS has today.

## Artwork: do not upload these as they are

The test server's library is commercial music (Metallica, Tool, Pink Floyd and so on), so every frame shows
copyrighted album art, a guideline 5.2 risk. `raw/` and `ios/store/screenshots/` are therefore git-ignored.
To produce uploadable screenshots, point `~/.config/s2-test/jellyfin.env` at a library of freely licensed music
(and update the album title in `maestro/open-first-album.yaml`), then run both commands above.
