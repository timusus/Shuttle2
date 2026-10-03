# Native surfaces — beyond the app window

What makes a music app feel native is mostly outside its own screens. For playback, the **system media
session does the work**: iOS `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` drive the Lock Screen,
Control Center, Dynamic Island and headphones; Android Media3 `MediaSession` drives the media
notification, system controls, Android Auto, Wear and Assistant. Get that exact before building anything
custom.

## Priority for Shuttle

| # | Surface | Android (state at writing) | iOS (state at writing) | Why |
|---|---|---|---|---|
| 1 | Media session / Now Playing | Present (`PlaybackService`) | Present (`NowPlayingController`) | Feeds every other surface; Android core-quality requirement |
| 2 | Car | Android Auto present | **CarPlay absent** | Driving is a primary listening context |
| 3 | Output routing | Cast present | AirPlay picker present | Core for server streaming |
| 4 | Widgets | Glance `NowPlayingWidget` present | **WidgetKit absent** | High visibility; Home + Lock Screen + StandBy |
| 5 | Intents / shortcuts | App shortcuts present | **App Intents absent** | One intents layer powers Siri, Spotlight, widgets, controls |
| 6 | Predictive back + mini player | On (targetSdk 36) — verify animations | n/a | In-app native feel |
| 7 | Quick controls | **QS tile absent** | **ControlWidget absent** | Sleep timer / shuffle all; niche |
| 8 | Watch | **Wear absent** | **watchOS absent** | Minority, costly (needs offline) |
| 9 | Live Activity / Live Update | n/a (media excluded) | Not for playback | Only for download/sync progress |

Re-check the state column against code before acting on it; it was a snapshot.

## iOS

- **Now Playing:** keep title, album, artist, artwork, elapsed, duration and rate exact and updated on
  seek/rate change; register play/pause, next/previous, `changePlaybackPosition`, like/dislike where
  supported. Pause on route loss.
- **Dynamic Island / Live Activities:** system Now Playing already appears in the Island — do not build a
  playback Live Activity. Valid custom uses: download/sync progress, sleep timer countdown. Design
  compact (leading + trailing), minimal (single glyph), expanded (one interactive element), tap deep-links
  to the right screen.
- **WidgetKit:** small/medium Home (Now Playing, Jump Back In, a playlist), accessory Lock Screen widgets;
  interactive play/pause and "play this" via App Intents. Must render in all iOS 26 appearances —
  light, dark, **clear** and **tinted** (art is desaturated; mark which images may stay full colour) — and
  in StandBy (scaled, no background).
- **Controls (iOS 18+ ControlWidget):** symbol + title + optional value; reflect in-progress state.
  Candidates: shuffle library, resume, sleep timer.
- **App Intents / App Shortcuts:** play album/artist/playlist, shuffle all, resume, add current song to
  playlist; parameterised by recent items; donate to Spotlight.
- **CarPlay:** audio apps are template-only (tab bar, list, grid, Now Playing templates) — design work is
  the browse tree, labels and artwork. Don't start an audio session until ready to play; don't autoplay
  except to resume.
- **AirPlay:** system route picker button, Apple symbol only, lower-right of the player.
- **Context menus, haptics, SF Symbol effects:** see ios.md.

## Android

- **Media notification & system controls:** on 13+ controls derive from the Player. Slot 1 play/pause
  (spinner while buffering); slots 2–3 previous/next or custom `CommandButton`s (`SLOT_BACK`/`SLOT_FORWARD`);
  overflow 4–5. Custom commands (favourite, shuffle) via media button preferences, authorised in
  `onConnect`. Support playback resumption. Core quality: MediaStyle notification, foreground service,
  audio focus, start immediately or show loading.
- **Glance widgets:** responsive sizes (2×1, 2×2, 4×2 targets; fill bounds; breakpoints), `GlanceTheme`
  with dynamic colour and an artwork-derived option; rounded corners from the system radius; preview
  image/layout for the picker. Interactive play/pause/skip.
- **Quick Settings tile:** frequent fast actions only (sleep timer toggle, shuffle all); ACTIVE/INACTIVE/
  UNAVAILABLE states; 24dp white vector icon; never just "open app".
- **App shortcuts:** four — Shuffle all, Recently added, a pinned/frequent playlist, Search; labels ≤ 10
  (short) / ≤ 25 (long) characters.
- **Android Auto:** `MediaLibraryService` browse tree; root ≤ 4 tabs (read the root-children hint); grid
  content style for albums/artists, list for songs; search; custom browse actions; art at car resolution.
- **Wear OS (if built):** Horologist media toolkit; 5-slot controls (2 or 3 visible by screen size);
  bezel/crown volume; prefer downloaded content; media Tile.
- **Live Updates (16):** MediaStyle is excluded — use `ProgressStyle` only for downloads/sync.
- **Predictive back, themed icon, per-app language, haptics, Cast** (Cast design checklist: controls in
  dialog, mini/expanded controller, notification, lock screen; stay in sync with the receiver).

## Design rules for surfaces

- Surfaces are glanceable: one job each, the current state visible, one tap to act.
- Use the same vocabulary everywhere ("Shuffle all", "Play next") — app, widgets, intents, Auto/CarPlay.
- Artwork everywhere a surface allows it, at the right resolution; generated artwork when missing.
- Every surface deep-links to the matching in-app screen, never just the app root.
