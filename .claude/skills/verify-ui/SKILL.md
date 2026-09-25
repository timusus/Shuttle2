---
name: verify-ui
description: After Compose UI changes, assess test coverage and write/amend Robolectric characterisation tests using the Robot pattern.
user_invocable: true
---

# Verify UI Changes

Assess uncommitted UI changes, determine if characterisation tests are needed, and write
them using this repo's Robot pattern. Prefer tests that exercise the real composable and
assert on user-visible text/nodes, faking only the ViewState/callbacks at the edges.

## Step 1: Identify What Changed

```bash
git diff --name-only HEAD
```

Classify each changed file:

| File location | Classification |
|---|---|
| Compose screens/composables under `android/app/src/main/.../ui/screens/` | **UI change** — likely needs a characterisation test |
| ViewModel, ViewState for a Compose screen | **Behavior change** — needs a characterisation test |
| `mediaprovider/`, `data/`, use cases | **Logic change** — unit test, not this skill |
| DI modules, navigation wiring | **Wiring change** — verify manually or via instrumented tests |

If no Compose UI files changed, stop — say "No UI changes detected, characterisation tests
not needed."

## Step 2: Find Existing Coverage

For each affected screen, look for its existing test trio next to the screen's ViewModel:

```bash
find android/app/src/test -iname "*ScreenName*Test.kt" -o -iname "*ScreenName*Robot.kt"
```

Read the matching `*Test.kt` and `*Robot.kt`. Ask:
- Does an existing test already exercise the changed behavior?
- Would the existing test catch a regression if this change broke something?
- Does the robot need updating (new test tag, changed content description, new interaction)?

**If existing tests cover the change:** Update them to match new behavior. Don't add
redundant tests.

**If no existing test covers the change:** Write a new trio (Step 3).

## Step 3: Write the Test

Each screen's tests live in three files alongside each other, e.g.
`android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/songs/`:

```
SongListTest.kt        # test cases
SongListRobot.kt       # selector/interaction encapsulation
SongListScenarios.kt   # ViewState factories
```

### Robot responsibilities — keep it thin

- `setContent(viewState)` — renders the composable with callback captures
- `assertTextDisplayed(text)` / `assertTextNotDisplayed(text)` — hides node selectors
- `openContextMenu()` — hides content description selectors
- `clickText(text)` / `clickMenuItem(text)` — interaction primitives
- Callback capture fields (`lastAddedToQueue`, `lastDeleted`, etc.) — avoid verbose lambda
  setup in tests
- The robot hides *selectors*, not *behaviour* — tests compose primitives to describe what
  they verify. No screen-specific compound assertions.

### Scenario factories

Top-level functions that construct `ViewState` with sensible defaults:

```kotlin
readySongList(songs = listOf(createSong(name = "My Song")))
scanningSongList(Progress(50, 200))
emptySongList()
loadingSongList  // val, not a function — Loading has no parameters
```

### Model factories

`createSong()`, `createGenre()`, `createPlaylist()` in
`android/app/src/test/java/com/simplecityapps/creationFunctions.kt`. All parameters have
defaults — override only what matters for the test.

### Example test

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

### Known Robolectric limitation: FastScroller + DropdownMenu

The `FastScroller` overlay causes `DropdownMenu` popups to be immediately dismissed under
Robolectric. Context menu tests that need dropdowns should render the list *item*
composable directly (e.g. `GenreListItem`) rather than the full list. The robot encapsulates
this — see `GenreListRobot.setItemContent()`.

## Step 4: Run and Verify

```bash
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.songs.SongListTest"
```

If the test fails, fix the test or the implementation — diagnose which is wrong before
changing either.

## When NOT to Write a Test

- The change is a string/copy update and existing tests already assert on other elements of
  the same screen
- The change is a dependency version bump with no behavior change
- An existing characterisation test already covers the changed composable and would catch
  regressions
