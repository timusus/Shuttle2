# Extract PlaybackManager/QueueManager interfaces for testability

## Goal

Replace `mockk(relaxed = true)` usage of `PlaybackManager` and `QueueManager` in unit/integration tests with fakes backed by extracted interfaces. This follows the project's testing strategy: "fakes at boundaries, not mocks of internal collaborators."

## Context

`PlaybackManager` and `QueueManager` are concrete classes in the `:android:playback` module. They're injected into ~26 and ~22 consumers respectively (ViewModels, Presenters, Services, etc.). Currently, `SongListIntegrationTest` and `GenreListIntegrationTest` use `mockk(relaxed = true)` for both because there are no interfaces to fake.

Read these docs before starting:
- `docs/architecture/compose-viewmodel-udf.md` — testing principles (section 14)
- `CLAUDE.md` — testing strategy section

Read these files to understand the current state:
- `android/playback/src/main/java/com/simplecityapps/playback/PlaybackManager.kt`
- `android/playback/src/main/java/com/simplecityapps/playback/queue/QueueManager.kt`
- `android/playback/src/main/java/com/simplecityapps/playback/di/PlaybackEngineModule.kt`
- `android/playback/src/main/java/com/simplecityapps/playback/di/PlaybackModule.kt`
- `android/app/src/test/java/com/simplecityapps/fakes/` — all existing fakes, for the pattern
- `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListIntegrationTest.kt`
- `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/genres/GenreListIntegrationTest.kt`

## Steps

### 1. Extract interface from PlaybackManager

Create `android/playback/src/main/java/com/simplecityapps/playback/PlaybackOperations.kt` (or similar name — check what fits the codebase conventions).

The interface should contain only the public API methods consumers call:

```
load(seekPosition: Int? = null, completion: (Result<Boolean>) -> Unit)
play(attempt: Int = 1)
togglePlayback()
skipToNext(ignoreRepeat: Boolean = false, completion: ((Result<Any?>) -> Unit)? = null)
skipToPrev(force: Boolean = false, completion: ((Result<Any?>) -> Unit)? = null)
skipTo(position: Int)
addToQueue(songs: List<Song>)                    // suspend
playNext(songs: List<Song>)                      // suspend
shuffle(songs: List<Song>, completion: ...)       // suspend
seekTo(position: Int)
playbackState(): PlaybackState
getProgress(): Int?
getDuration(): Int?
getPlaybackSpeed(): Float
setPlaybackSpeed(multiplier: Float)
moveQueueItem(from: Int, to: Int)
removeQueueItem(queueItem: QueueItem)
clearQueue()
getPlayback(): Playback
switchToPlayback(playback: Playback)
```

Do NOT include the callback interface implementations (`Playback.Callback`, `AudioFocusHelper.Listener`, `QueueChangeCallback`) — those are internal playback machinery. The interface is the consumer-facing API.

Make `PlaybackManager` implement the new interface. Update the DI module to bind the interface type.

### 2. Extract interface from QueueManager

Create `android/playback/src/main/java/com/simplecityapps/playback/queue/QueueOperations.kt` (or similar).

Public API methods consumers call:

```
setQueue(songs: List<Song>, shuffleSongs: List<Song>? = null, position: Int = 0): Boolean  // suspend
getQueue(): List<QueueItem>
getQueue(shuffleMode: ShuffleMode): List<QueueItem>
getCurrentItem(): QueueItem?
getCurrentPosition(): Int?
getSize(): Int
setCurrentItem(currentItem: QueueItem)
getNext(ignoreRepeat: Boolean = false): QueueItem?
getPrevious(): QueueItem?
skipToNext(ignoreRepeat: Boolean = false): Boolean
skipToPrevious()
skipTo(position: Int)
addToQueue(songs: List<Song>)
addToNext(songs: List<Song>)
move(from: Int, to: Int)
remove(items: List<QueueItem>)
remove(song: Song)
clear()
getShuffleMode(): ShuffleMode
setShuffleMode(shuffleMode: ShuffleMode, reshuffle: Boolean)  // suspend
toggleShuffleMode()                                            // suspend
getRepeatMode(): RepeatMode
setRepeatMode(repeatMode: RepeatMode)
toggleRepeatMode()
hasRestoredQueue: Boolean
```

Make `QueueManager` implement the new interface. Update the DI module to bind the interface type.

### 3. Update all injection sites

Every constructor that takes `PlaybackManager` or `QueueManager` should take the interface type instead. This is ~26 files for PlaybackManager and ~22 for QueueManager. Many overlap. The change is mechanical: just change the type in the constructor parameter.

**Important:** `PlaybackManager` itself takes `QueueManager` in its constructor. It needs the concrete class (it calls internal methods and implements `QueueChangeCallback`). Keep the concrete type there — only change consumer injection sites.

### 4. Create FakePlaybackManager

Create `android/app/src/test/java/com/simplecityapps/fakes/FakePlaybackManager.kt`.

Follow the pattern of existing fakes (`FakeSongRepository`, `FakeGenreRepository`):
- Implement the interface
- Use simple properties for state
- No-op for fire-and-forget methods
- Track calls if useful for assertions (e.g. `addedToQueue: List<Song>`)

For the integration tests that only test state derivation (not side effects), most methods can be no-ops. But make `setQueue()` return `true` by default so `onPlay()` paths don't silently skip.

### 5. Create FakeQueueManager

Create `android/app/src/test/java/com/simplecityapps/fakes/FakeQueueManager.kt`.

Same pattern. Key behaviors:
- `setQueue()` returns `true` by default
- `getQueue()` returns an empty list by default
- `remove()` is a no-op

### 6. Update tests to use fakes

Replace `mockk(relaxed = true)` with the new fakes in:
- `SongListIntegrationTest.kt`
- `GenreListIntegrationTest.kt`
- `SongListViewModelTest.kt`

### 7. Verify

Run all unit tests:
```bash
./gradlew testDebugUnitTest
```

Run GMD instrumented tests:
```bash
./gradlew :android:app:pixel6Api34AtdDebugAndroidTest
```

Run lint:
```bash
support/scripts/lint
```

## Constraints

- Don't rename the concrete classes
- Don't change any behaviour — this is a pure refactor for testability
- Don't touch the internal playback machinery (callbacks, Playback.Callback, etc.)
- The concrete `PlaybackManager` constructor still takes concrete `QueueManager` — only consumer injection sites change to interfaces
- Commit directly to main (trunk-based, per project conventions)
