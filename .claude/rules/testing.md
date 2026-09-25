---
paths:
  - "android/**/src/test/**"
  - "android/**/src/androidTest/**"
---

# Android tests

### Compose UI Characterisation Tests

Robolectric-based Compose tests that verify observable UI behaviour. These allow safe rearchitecting of Compose screens and ViewModels — if the UI still looks right, the tests pass.

**Run them:**
```bash
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.songs.SongListTest"
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.genres.GenreListTest"
```

**Configuration:** `android/app/src/test/resources/robolectric.properties` sets `sdk=34`, `graphics=NATIVE`, and `application=android.app.Application` (bypasses Hilt app init for fast, isolated tests).

## Robot Pattern

Each Compose screen has a **robot** that encapsulates selectors and interaction mechanics. Tests express *what* they verify, not *how* to find Compose nodes. When the implementation changes (test tags, content descriptions, layout structure), update the robot — not every test.

**Files per screen:**
```
songs/
  SongListTest.kt        # test cases
  SongListRobot.kt       # selector/interaction encapsulation
  SongListScenarios.kt   # ViewState factories
```

**Robot responsibilities:**
- `setContent(viewState)` — renders the composable with callback captures
- `assertTextDisplayed(text)` / `assertTextNotDisplayed(text)` — hides node selectors
- `openContextMenu()` — hides content description selectors
- `clickText(text)` / `clickMenuItem(text)` — interaction primitives
- Callback capture fields (`lastAddedToQueue`, `lastDeleted`, etc.) — avoid verbose lambda setup in tests

**Robot boundaries — keep it thin:**
- The robot hides *selectors* (content descriptions, test tags, node matchers)
- The robot does NOT hide *behaviour* — tests compose primitives to describe what they verify
- Assertions use user-visible text, not implementation details
- No screen-specific compound assertions like `assertContextMenuComplete()` — tests list what they expect

**Example test:**
```kotlin
@Test
fun `context menu invokes onAddToQueue`() {
    val song = createSong(name = "Queue Me")
    robot.setContent(readySongList(songs = listOf(song)))
    robot.openContextMenu()
    robot.clickMenuItem("Add to Queue")
    robot.lastAddedToQueue shouldBe song
}
```

## Scenario Factories

Top-level functions that construct `ViewState` with sensible defaults. Reduce boilerplate without hiding what matters.

```kotlin
// SongListScenarios.kt
readySongList(songs = listOf(createSong(name = "My Song")))
scanningSongList(Progress(50, 200))
emptySongList()
loadingSongList  // val, not a function — Loading has no parameters
```

## Model Factories

`createSong()`, `createGenre()`, `createPlaylist()` in `app/src/test/.../creationFunctions.kt`. All parameters have defaults — override only what matters for the test.

## Sample Library (screenshots)

Recordings (Roborazzi, catalogue boards) show the invented sample library, never real artists,
albums or songs, and never hand-typed "Artist"/"Album" stand-ins where content is visible.

- **Data and covers:** `:android:fixtures` (`SampleLibrary`: albums, songs, artists, genres,
  playlists, `queue()`, `cover(albumId)`), read from
  `android/fixtures/src/main/resources/sample-library/library.json`. Add it as
  `testImplementation(project(":android:fixtures"))` (designsystem and app also use
  `debugImplementation` plus `releaseCompileOnly`, for the boards and `@Preview`s). To change
  names or covers, edit the manifest and rerun `support/scripts/generate-fake-artwork.py`; the
  contact sheet is
  `docs/design/fake-artwork/contact-sheet.png`.
- **App models:** `SampleSong.toSong()`, `toAlbum()`, `toAlbumArtist()`, `toGenre()`,
  `toPlaylist()` and `sampleSongs(n)` in `app/src/main/.../ui/preview/SamplePreviews.kt` — shared
  by `@Preview`s and tests. Release compiles against `:android:fixtures` but never packages it
  (`releaseCompileOnly`); nothing yet catches live code that reaches it at build time (#402).
- **Artwork through Glide:** call `SampleArtworkGlide.install(context)` in `@Before` and
  `uninstall()` in `@After`. Songs, albums and album artists named after sample ones then
  load their covers synchronously. Nothing else loads, so other content keeps its placeholder.
  Production image loading is untouched. See `ShellScreenshotTest`.
- **Previews:** wrap a `@Preview` in `S2Preview { }` (designsystem) and pass the sample model as
  `Artwork(model = ...)`; app previews use `S2Preview(artwork = SampleAppCovers)` or
  `SampleArtwork { }` inside their own theme, with models from `ui/preview/SamplePreviews.kt`.
  Covers draw synchronously through `LocalPreviewArtwork`, no Glide; keep fixture data in
  previews out of top-level fields (release has no fixtures). See `PreviewArtworkTest`.
- **Boards:** `SampleArt(albumId)` for a row naming a sample album, `SampleArt(variant)` for
  generic art matched to the scheme column.

## Adding a New Screen's Tests

1. Create `*Robot.kt` — constructor takes `ComposeContentTestRule`, provides `setContent()`, selectors, callback captures
2. Create `*Scenarios.kt` — top-level functions for each ViewState variant
3. Create `*Test.kt` — `@RunWith(RobolectricTestRunner::class)`, instantiate robot from `composeTestRule`
4. Add model factories to `creationFunctions.kt` if needed

## Known Robolectric Limitations

- **FastScroller + DropdownMenu:** The `FastScroller` overlay causes `DropdownMenu` popups to be immediately dismissed under Robolectric. Context menu tests that need dropdowns should render the list *item* composable directly (e.g. `GenreListItem`) rather than the full list. The robots encapsulate this — see `setItemContent()` in `GenreListRobot`, `PlaylistListRobot`, `AlbumListRobot`, `AlbumArtistListRobot` and `FolderListRobot`.
- **Text field inside a Dialog window:** Compose never goes idle under Robolectric when a `TextField` is focused in a `Dialog`, so the test hangs. Test the dialog's form composable on its own, outside the dialog (see `NewPlaylistFormTest`), and stop screen-level tests at the step that opens the dialog.
