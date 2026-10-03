# Music-player patterns

Shuttle mixes a local library with Jellyfin/Emby/Plex streaming. These rules apply on both platforms;
idioms per platform come from android.md / ios.md.

## Artwork colour — the signature

Shuttle did art-derived theming first and best; it must stay the best implementation in the category,
on both platforms. Treat it as a system, not a player effect.

- **Reach:** Now Playing and the mini player always; album/artist/playlist detail from their own art;
  optionally the whole app chrome following the current track (a user-facing choice alongside accent
  and dynamic colour, not hidden). Widgets, Auto/CarPlay art and Live surfaces carry the same colours
  where the platform allows.
- **Quality bar:** a full, contrast-safe scheme (MaterialKolor `SchemeContent`/fidelity-style from the
  art seed on Android; `ArtworkPalette`/`PlayerPalette` on iOS), not a single dominant colour. Text and
  controls ≥ 4.5:1 / 3:1 in light and dark; tone-clamp muddy, near-grey and neon art; monochrome art gets
  a tasteful neutral, not a random hue.
- **Craft:** multi-colour where it helps (gradient or blurred art backdrop on Now Playing, as Plexamp's
  UltraBlur), tinted controls, tinted progress. Track-to-track transitions animate the scheme (effects
  spring / ~400–600ms crossfade), never flash; pre-extract the next track's palette so it is ready.
- **Performance:** extract off the main thread from a small downscaled image (~112px), cache per album
  key; the first frame uses the cached or neutral scheme, never blocks.
- **Parity:** same intent on both platforms, native rendering (tonal surfaces on Android, tint + glass
  over art on iOS with dimming for bright art).
- **Respect:** Reduce Motion → instant swap; Increase Contrast → push tones further apart; a setting to
  turn it off.
- Competitors now copy this (Plexamp, Gramophone, Namida, Finamp); it only stays a differentiator if it
  is visibly more refined than theirs — judge it side by side in critiques.

## Now Playing

- **Hierarchy:** artwork → title/artist → scrubber with elapsed/remaining → transport → secondary row
  (like, queue, lyrics, output/Cast/AirPlay, more). Nothing else on the first screen.
- **Artwork** is the largest element on compact; square, continuous corners, subtle shadow; swipe on art
  skips tracks (with peek of next art) — but skip buttons remain; gestures are accelerators, never the
  only path.
- **Colour from art:** derive the scheme/tint from the artwork (MaterialKolor scheme on Android,
  `PlayerPalette`/`ArtworkTint` on iOS); guarantee text ≥ 4.5:1 against the derived background — clamp
  tone, don't trust raw dominant colour. Animate between tracks' palettes with an effects spring
  (~400–600ms), never a flash.
- **Transport:** play/pause is the largest control and optically centred; skip either side; shuffle and
  repeat at the extremes or in the secondary row, showing state (mode, not just on/off for repeat-one).
- **Scrubber:** large touch area, tabular numbers, thumb grows on drag, haptic detent at start; show
  buffered range for streams.
- **Dismiss:** drag down anywhere on the art/handle region; Android predictive back collapses into the
  mini player.
- **Landscape/tabletop/tablet:** see adaptive.md tier table; the player never scrolls on phone.
- **Lyrics** (when available): time-synced, current line emphasised, tap a line to seek; artwork shrinks
  rather than disappears.

## Mini player

- Always visible while something is queued, above navigation (Android) / as the tab bar accessory (iOS).
- Content: small art, title (and artist), play/pause, one more action at most (skip next). Progress as a
  thin line or ring.
- Tap or swipe up expands with a shared-element transition (art grows into the hero art); swipe
  horizontally to skip.
- Never covers the last list row — lists add its height to bottom content padding.

## Queue

- Reachable in one step from Now Playing; on Expanded+ docked beside it.
- Current item pinned/visible with "Up next" count and remaining time.
- Reorder by drag handle (with haptics and accessibility "move up/down" actions); remove by swipe
  (Android: with Undo snackbar; iOS: swipe action) — both have an accessible non-gesture alternative.
- Distinguish history vs up next; "Play next" vs "Add to queue" are separate, consistently worded actions.

## Library browsing

- Roots: Home (Jump Back In, recently added, recently played, playlists), Library (Albums, Artists, Songs,
  Playlists, Genres, Folders), Search.
- Albums default to a grid with adaptive columns; songs to a list; artists to list with round art or grid.
- **Album artist** is the artist unit for browsing; track artists appear on rows only when they differ.
- Sort and filter in a compact control in the app bar (persisted per view); a fast-scroll letter index for
  alphabetic sorts on long lists (both platforms).
- Album detail: hero art + title (emphasized type) + artist + year/track count/duration + quality
  badge; primary actions Play and Shuffle; disc headers for multi-disc; tracks numbered.
- Empty library → a guided empty state that leads to adding a source, not a blank list.

## Multi-source and streaming specifics

- **Merge, then filter:** one merged library; a "playable now / offline" filter beats badging every row.
  Source appears on detail and Song Info, not on every row. Availability: unavailable items (server
  offline) are dimmed, not hidden, and explain themselves on tap.
- **Where music goes:** "Play", "Shuffle", "Play next", "Add to queue" are distinct, consistently worded
  actions (Finamp's Playback Row is the reference).
- **Download state** on rows and albums: not downloaded / queued / progress ring / downloaded; bulk
  download on album/playlist.
- **Quality:** codec/bitrate/sample-rate badge on Now Playing and song info; indicate transcoding vs
  direct play. Lossless/hi-res as a quiet badge, not a sticker.
- **Latency:** stream start shows a loading state on the play button within 100ms (core app quality:
  start immediately or indicate loading); artwork loads progressively with generated placeholder.
- **Server sign-in** and sync are flows with clear progress and recoverable errors; never block the
  library UI while syncing.

## Behaviour (platform manners)

- Headphones/Bluetooth disconnect → pause immediately; after an interruption (call, other audio) resume
  only if the interruption system says so.
- Never start playback on launch unless the user asked (resume button is offered instead — Jump Back In).
- Remember position for long tracks (audiobooks/podcasts, mixes) and expose playback speed only there or
  in settings.
- Sleep timer, replay gain, EQ: reachable from Now Playing's more menu; settings show the current value.
