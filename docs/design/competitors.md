# Competitor reference (as of Oct 2026)

Prior art for `shape` and `critique`. Cite an app when borrowing or rejecting a pattern. Some claims come
from press and forums rather than hands-on use; [unverified] marks thin ones. Refresh this file when a
major competitor redesigns.

## Android

| App | Known for | Steal | Avoid |
|---|---|---|---|
| Symfonium | Best multi-server client (Plex/Emby/Jellyfin/Subsonic/local merged) | Codec/bitrate as optional row info; "playable now / offline" filters; per-rule transcode for downloads; secondary (home/away) server address; left rail on tablets; Wear app with downloads | Settings sprawl with no search or live preview; Now Playing built from knobs ([v14](https://symfonium.app/news/version-1400/), [thread](https://support.symfonium.app/t/real-time-preview-for-ui-and-parameter-customization/13282)) |
| YouTube Music | 2025 Now Playing redesign, widely praised | Bottom drag handle labelled with the playing context, opening a controls + next-tracks split; thumbless progress bar that thickens while scrubbing; tap title for song details ([9to5G](https://9to5google.com/2025/12/20/youtube-music-2025-now-playing-redesign/)) | — |
| Spotify | 2026 tablet/foldable redesign | Persistent Now Playing pane (lyrics/queue) on the right of a collapsible sidebar; layout reconfigures per orientation ([9to5G](https://9to5google.com/2026/04/16/spotify-tablet-foldable-redesign-update/)) | Moving queue/repeat into overflow and controls up the screen — reverted after backlash |
| Auxio | Opinionated, "ease of use over edge cases" | Merged artist/album-artist model, release types, disc headers; tabs that don't reshuffle per configuration; fast scroll in detail; small split-screen layouts; responsive widgets ([v4](https://github.com/OxygenCobalt/Auxio/releases/tag/v4.0.0)) | — |
| Gramophone | Strict platform conformance | Monet colour throughout; word-synced lyrics (LRC/TTML); format shown on Now Playing ([repo](https://github.com/FoedusProgramme/Gramophone)) | Expressive adoption unconfirmed |
| Plexamp | Visual flair | UltraBlur multi-colour art gradient with optional tinted controls; swipe up to peek at the queue | Hides library browsing; sloppy Android landscape (no cut-out extension) ([forum](https://forums.plex.tv/t/plexamp-landscape-android/927594)) |
| Finamp | Jellyfin, redesign beta | "Playback Row": New Queue / Play Next / Append / Play Last, each shuffle-able, remembering the last choice; playable disc headers ([0.9.20](https://newreleases.io/project/github/finamp-app/finamp/release/0.9.20-beta)) | — |
| Poweramp | Large libraries, audio engine | — | Wave seekbar with an imprecise hit area; cover/artist taps doing unexpected things ([forum](https://forum.powerampapp.com/topic/15884-the-wave-bar-problem/)) |
| Musicolet | Offline power user | Several saved queues, each remembering position (consider as "resume previous queue") | Busy UI |
| Retro/Metro, Namida | Customisation, visual effects | — | 10+ selectable player layouts; particle/party effects — customisation instead of opinion [Namida criticism unverified] |

## iOS

| App | Steal | Avoid |
|---|---|---|
| Apple Music (iOS 26) | Mini player as tab-bar accessory beside the shrinking glass tab bar; swipe the mini player to skip (26.1); up to 6 library pins; playlist folders; animated Lock Screen art ([MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3)) | Translucent mini player unreadable over content (beta 2) — glass needs dimming/tint |
| Doppler | Pinnable album collections that also appear in CarPlay; queue grouped by album; interactive widgets; Dynamic Type done properly | — |
| Marvis Pro | Section-based home (~30 section types, each with layout choice) | Unrefined gestures/transitions; bland greyscale ([MacStories](https://www.macstories.net/reviews/marvis-review-the-ultra-customizable-apple-music-client/)) |
| Manet, Amperfy | Jellyfin/Subsonic clients with CarPlay, Siri, Shortcuts, offline — the native-surface baseline for server players | — |

## Feature expectations (audit pass 4)

The music-app baseline an audit compares Shuttle against, distilled from the tables above. The
whole-app audit (evaluate.md) walks every row per platform, marks it present / partial / absent, and
files each miss as a finding — a missing expectation is a defect even when everything that exists is
beautiful. Rows in the mobile-design skill's native-surfaces.md say where each surface is checked.

Core playback (both platforms):

- Persistent mini player: swipe-to-skip, expands to Now Playing with a shared-element transition.
- Queue one tap from Now Playing: reorder by drag, history vs up next, "Play next" vs "Add to queue".
- Now Playing: artwork-led, art-derived colour as a full contrast-safe scheme, scrubber with buffered
  range, sleep timer and EQ reachable from the more-menu.
- Gapless playback, ReplayGain.
- Playback resumption offered (never autoplay on launch); position remembered for long tracks.
- Headphone/Bluetooth disconnect pauses; resume after interruption only when the system says so.

Library (both platforms):

- Merged local + server library; "playable now / offline" filter, not per-row badges.
- Album/artist/playlist detail with quality line (codec/bitrate/sample rate, transcode vs direct).
- Search across the merged library; sort/filter persisted per view.
- Downloads: per-row state, bulk download on album/playlist.

Platform surfaces:

- Android: dynamic colour, edge-to-edge, M3 Expressive (wavy) progress as in the system media
  controls, real large-screen layouts, MediaStyle notification, Android Auto, size-adaptive widgets,
  app shortcuts.
- iOS: tab-bar-accessory mini player, system Now Playing on Lock Screen/Control Center/Watch via the
  media session, CarPlay, Dynamic Type, WidgetKit widgets, App Intents/Siri.
- Both: Auto/CarPlay, synced lyrics (word-level ideal), library pins.

## Anti-patterns users punish

Burying core controls in overflow · settings sprawl without search/preview · novelty seekbars that lose
precision · taps that do something surprising · undiscoverable gestures · translucent chrome over busy
content · neglected landscape · hiding the library · badges on everything (Symfonium's developer resisted
cache badges as clutter, later shipped them optional).

## Android vs iOS — where Android players lose

Weak motion and polish; customisation instead of opinion; neglected large screens. Auxio (opinion),
Gramophone (conformance), Spotify 2026 (large screens) show the way out. Shuttle's Android direction:
opinionated defaults, conventional Material behaviour with selective Expressive styling and Shuttle's
own artwork-led look, a deliberate layout per tier.

## Shuttle's own history (Oct 2026 research)

- Original Shuttle (2012–2020): ~5M installs; press called it "probably the best-looking music app on
  Android" ([GSMArena](https://www.gsmarena.com/top_music_player_apps_for_android-news-25410.php)).
  Peer group: Phonograph, Pulsar, BlackPlayer — the "beautiful Material player" tier, not Poweramp's.
  Praised for looks and simplicity; criticised for "major usability problems" in its later years.
- S2 / Shuttle Music: 100K+ installs, ~4.0 public rating; absent from 2025–2026 Jellyfin/self-hosted
  roundups (Finamp, Symfonium, Plexamp own that space). Users value: fast, simple, battery-light, own tag
  reader (accurate library), one-time price, Android Auto. Users complain about: library organisation
  (no folder hierarchy, jumbled large libraries, playlist ordering, multi-disc/OST grouping), and the
  trial penalty (being removed).
- Design implications: keep "the beautiful one" as the brand; make library correctness and organisation
  visibly first-class (folder view, grouping, sorting); lead with the local + server hybrid.

## Highest-value moves for Shuttle (from this survey)

1. Android Now Playing: YouTube Music-style context handle → split controls + queue view; Expressive wavy
   progress with an exact, thickening scrub state.
2. Expanded+ shell with a persistent Now Playing/queue pane (Spotify 2026).
3. Finamp-style Playback Row for where music goes (matters more with multiple sources).
4. Quality line on Now Playing ("FLAC 24/96 → Opus 192k, transcoded") and an offline/playable filter
   instead of per-row badges.
5. Opinionated defaults; at most a few presets, never a wall of knobs.
6. Library pins and mini-player swipe-to-skip on both platforms.
