# Prompt: Redesign album and artist detail screen headers

## Context

The album detail and artist detail screens were recently migrated from MVP to Compose + ViewModel. The current implementation uses a `DetailScaffold` with a pinned `SmallTopAppBar` (back + overflow) and a full-width artwork image as the first LazyColumn item, followed by metadata and content.

Read these before starting:
- `docs/architecture/compose-viewmodel-udf.md`
- `CLAUDE.md`
- The podcast app screenshot at `docs/migration-specs/album-artist-detail/podcast-reference.webp` (reference for the header layout)

## The change

Replace the full-width hero artwork with a compact header layout matching the podcast app pattern. For both album detail and artist detail screens:

### Header layout (single LazyColumn item)

```
[Artwork 80dp]  [Title - headline small        ]
[             ]  [Subtitle - body medium, muted ]
```

- Artwork: 80dp square, rounded corners (8dp), left-aligned
- Title: album/artist name, `headlineSmall`
- Subtitle: metadata line (year · songs · duration for albums; albums · songs for artists)
- The artwork and text sit side by side in a Row

### Below the header

- Action chips or buttons (Play, Shuffle) — optional, can be added later
- Content: track list (album detail) or albums + songs sections (artist detail)

### Toolbar

- Pinned `SmallTopAppBar` via `DetailScaffold`
- Back arrow + overflow menu (same as current)
- No title text in the toolbar — it stays empty

### What stays the same

- `DetailScaffold` composable (pinned toolbar + LazyColumn)
- ViewModel, UiState, UiEvent — no changes
- Fragment wiring — no changes
- Song/album item composables — no changes
- All existing tests should still pass (update robots if needed)

## Files to change

- `AlbumDetail.kt` — replace full-width GlideImage + AlbumMetadataHeader with compact header Row
- `AlbumArtistDetail.kt` — replace full-width GlideImage + metadata Column with compact header Row
- Test robots/scenarios if the composable signature changes

## Design rationale

- M3 has no collapsing image header component — the old CoordinatorLayout pattern is M1 legacy
- Compact header is more information-dense: artwork, title, metadata, and first few tracks all visible without scrolling
- Better suited for future container transform shared element transitions (small artwork is a natural morph target from grid tiles)
- Matches the pattern used in the podcast app and Google's own M3 apps
- Far simpler to implement and maintain than any collapsing/parallax approach

## What NOT to do

- No collapsing toolbar, parallax, or scroll-based animations
- No changes to ViewModel or Fragment
- No full-width hero image
- Don't over-engineer — this is a straightforward layout change
