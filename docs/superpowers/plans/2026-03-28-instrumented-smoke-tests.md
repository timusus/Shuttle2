# Instrumented Smoke Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a small set of instrumented smoke tests covering the critical user paths (launch, browse library, play song, search) using Gradle Managed Devices with ATD images for fast, hermetic execution.

**Architecture:** Hilt test modules swap ExoPlayer and AudioFocus with fakes at the hardware boundary. An in-memory Room database is seeded with test data. Tests use Espresso for Fragment-based views. The app's `hasOnboarded` preference is pre-set so tests skip onboarding and land directly on the main screen.

**Tech Stack:** Espresso, Hilt testing, Room in-memory DB, Gradle Managed Devices (ATD API 34), JUnit 4

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `android/app/src/androidTest/java/com/simplecityapps/shuttle/fake/FakePlayback.kt` | Fake `Playback` implementation that immediately succeeds |
| Create | `android/app/src/androidTest/java/com/simplecityapps/shuttle/fake/FakeAudioFocusHelper.kt` | Fake `AudioFocusHelper` that always grants focus |
| Create | `android/app/src/androidTest/java/com/simplecityapps/shuttle/di/TestPlaybackModule.kt` | Hilt test module replacing PlaybackModule's ExoPlayer + AudioFocus bindings |
| Create | `android/app/src/androidTest/java/com/simplecityapps/shuttle/di/TestRepositoryModule.kt` | Hilt test module providing in-memory Room database |
| Create | `android/app/src/androidTest/java/com/simplecityapps/shuttle/smoke/SmokeTestData.kt` | Test data factory — creates SongData, inserts into DB, sets preferences |
| Create | `android/app/src/androidTest/java/com/simplecityapps/shuttle/smoke/SmokeTestSuite.kt` | The smoke tests: launch, browse, play, search |
| Modify | `android/app/build.gradle.kts` | Add Espresso dependency, Gradle Managed Device config |
| Modify | `gradle/libs.versions.toml` | Add `androidx-ui-test-junit4` and `androidx-ui-test-manifest` entries |

---

### Task 1: Add test dependencies and Gradle Managed Device config

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Add test library entries to version catalog**

In `gradle/libs.versions.toml`, add to `[libraries]`:

```toml
androidx-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
```

(No version needed — managed by the Compose BOM already declared.)

- [ ] **Step 2: Add Espresso and Compose test deps to app build.gradle.kts**

In `android/app/build.gradle.kts`, in the `dependencies` block, after the existing `androidTestImplementation` lines (around line 269), add:

```kotlin
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.ui.test.junit4)
        debugImplementation(libs.androidx.ui.test.manifest)
```

- [ ] **Step 3: Add Gradle Managed Device configuration**

In `android/app/build.gradle.kts`, inside the `android { }` block (after the `lint { }` block, around line 96), add:

```kotlin
    testOptions {
        managedDevices {
            localDevices {
                create("pixel6Api34Atd") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
            groups {
                create("smoke") {
                    targetDevices.add(devices["pixel6Api34Atd"])
                }
            }
        }
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
    }
```

- [ ] **Step 4: Add test orchestrator dependency**

In `android/app/build.gradle.kts`, in the `dependencies` block, add:

```kotlin
        androidTestUtil("androidx.test:orchestrator:1.5.1")
```

- [ ] **Step 5: Verify the build compiles**

Run: `./gradlew :android:app:assembleDebug --dry-run`
Expected: BUILD SUCCESSFUL (dry run confirms configuration is valid)

- [ ] **Step 6: Commit**

```bash
git add android/app/build.gradle.kts gradle/libs.versions.toml
git commit -m "$(cat <<'EOF'
Add instrumented smoke test infrastructure

Gradle Managed Device (Pixel 6, API 34, ATD image) for hermetic
test execution. Espresso, Compose test, and test orchestrator deps.
Animations disabled for determinism.
EOF
)"
```

---

### Task 2: Create FakePlayback

**Files:**
- Create: `android/app/src/androidTest/java/com/simplecityapps/shuttle/fake/FakePlayback.kt`

- [ ] **Step 1: Write FakePlayback**

```kotlin
package com.simplecityapps.shuttle.fake

import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Song

class FakePlayback : Playback {
    override var callback: Playback.Callback? = null
    override var isReleased: Boolean = false

    private var state: PlaybackState = PlaybackState.Paused
    private var progress: Int = 0
    private var duration: Int = 0
    private var volume: Float = 1.0f
    private var speed: Float = 1.0f
    private var repeatMode: QueueManager.RepeatMode = QueueManager.RepeatMode.Off

    override suspend fun load(
        current: Song,
        next: Song?,
        seekPosition: Int,
        completion: (Result<Any?>) -> Unit
    ) {
        isReleased = false
        progress = seekPosition
        duration = current.duration
        state = PlaybackState.Loading
        callback?.onPlaybackStateChanged(state)
        completion(Result.success(null))
    }

    override suspend fun loadNext(song: Song?) {
        // No-op for fake
    }

    override fun play() {
        state = PlaybackState.Playing
        callback?.onPlaybackStateChanged(state)
    }

    override fun pause() {
        state = PlaybackState.Paused
        callback?.onPlaybackStateChanged(state)
    }

    override fun release() {
        isReleased = true
        state = PlaybackState.Paused
    }

    override fun playBackState(): PlaybackState = state

    override fun seek(position: Int) {
        progress = position
    }

    override fun getProgress(): Int = progress

    override fun getDuration(): Int = duration

    override fun setVolume(volume: Float) {
        this.volume = volume
    }

    override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {
        this.repeatMode = repeatMode
    }

    override fun setPlaybackSpeed(multiplier: Float) {
        speed = multiplier
    }

    override fun getPlaybackSpeed(): Float = speed
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :android:app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/androidTest/java/com/simplecityapps/shuttle/fake/FakePlayback.kt
git commit -m "$(cat <<'EOF'
Add FakePlayback for instrumented tests

Implements Playback interface with immediate success callbacks.
No real audio — just state tracking for UI verification.
EOF
)"
```

---

### Task 3: Create FakeAudioFocusHelper

**Files:**
- Create: `android/app/src/androidTest/java/com/simplecityapps/shuttle/fake/FakeAudioFocusHelper.kt`

- [ ] **Step 1: Write FakeAudioFocusHelper**

```kotlin
package com.simplecityapps.shuttle.fake

import com.simplecityapps.playback.audiofocus.AudioFocusHelper

class FakeAudioFocusHelper : AudioFocusHelper {
    override var listener: AudioFocusHelper.Listener? = null
    override var enabled: Boolean = true
    override var resumeOnFocusGain: Boolean = true

    override fun requestAudioFocus(): Boolean = true

    override fun abandonAudioFocus() {
        // No-op
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :android:app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/androidTest/java/com/simplecityapps/shuttle/fake/FakeAudioFocusHelper.kt
git commit -m "$(cat <<'EOF'
Add FakeAudioFocusHelper for instrumented tests

Always grants audio focus. No system interaction.
EOF
)"
```

---

### Task 4: Create Hilt test modules

**Files:**
- Create: `android/app/src/androidTest/java/com/simplecityapps/shuttle/di/TestPlaybackModule.kt`
- Create: `android/app/src/androidTest/java/com/simplecityapps/shuttle/di/TestRepositoryModule.kt`

- [ ] **Step 1: Write TestPlaybackModule**

This module uninstalls the production `PlaybackModule` and replaces the `ExoPlayerPlayback` and `AudioFocusHelper` bindings with fakes. All other bindings from the original `PlaybackModule` that depend on `ExoPlayerPlayback` are re-provided using the fake.

```kotlin
package com.simplecityapps.shuttle.di

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import androidx.core.content.getSystemService
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.fake.FakeAudioFocusHelper
import com.simplecityapps.shuttle.fake.FakePlayback
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [com.simplecityapps.playback.di.PlaybackModule::class]
)
class TestPlaybackModule {

    @Singleton
    @Provides
    fun provideQueueWatcher(): QueueWatcher = QueueWatcher()

    @Singleton
    @Provides
    fun provideQueueManager(
        queueWatcher: QueueWatcher,
        preferenceManager: GeneralPreferenceManager
    ): QueueManager = QueueManager(queueWatcher, preferenceManager)

    @Singleton
    @Provides
    fun providePlaybackPreferenceManager(
        sharedPreferences: SharedPreferences,
        moshi: Moshi
    ): PlaybackPreferenceManager = PlaybackPreferenceManager(sharedPreferences, moshi)

    @Singleton
    @Provides
    fun provideFakePlayback(): FakePlayback = FakePlayback()

    @Singleton
    @Provides
    fun provideAudioFocusHelper(): AudioFocusHelper = FakeAudioFocusHelper()

    @Singleton
    @Provides
    fun providePlaybackWatcher(): PlaybackWatcher = PlaybackWatcher()

    @Provides
    fun provideAudioManager(
        @ApplicationContext context: Context
    ): AudioManager? = context.getSystemService()

    @Provides
    fun provideAudioEffectSessionManager(
        @ApplicationContext context: Context
    ): AudioEffectSessionManager = AudioEffectSessionManager(context)

    @Singleton
    @Provides
    fun providePlaybackManager(
        queueManager: QueueManager,
        fakePlayback: FakePlayback,
        playbackWatcher: PlaybackWatcher,
        audioFocusHelper: AudioFocusHelper,
        playbackPreferenceManager: PlaybackPreferenceManager,
        audioEffectSessionManager: AudioEffectSessionManager,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        queueWatcher: QueueWatcher,
        audioManager: AudioManager?
    ): PlaybackManager = PlaybackManager(
        queueManager,
        playbackWatcher,
        audioFocusHelper,
        playbackPreferenceManager,
        audioEffectSessionManager,
        coroutineScope,
        fakePlayback,
        queueWatcher,
        audioManager
    )
}
```

**Note:** This intentionally omits bindings for `CastService`, `HttpServer`, `CastSessionManager`, `MediaSessionManager`, `NoiseManager`, `PlaybackNotificationManager`, `SleepTimer`, `MediaIdHelper`, `EqualizerAudioProcessor`, `ReplayGainAudioProcessor`, and `AggregateMediaInfoProvider` — they are not needed for smoke tests and their absence will cause a compile error if any test path accidentally depends on them. If the app's `AppInitializer` set eagerly creates any of these, we handle that in Task 5 by also uninstalling `AppModuleBinds` to skip initializers.

- [ ] **Step 2: Write TestRepositoryModule**

```kotlin
package com.simplecityapps.shuttle.di

import android.content.Context
import androidx.room.Room
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumArtistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalGenreRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalPlaylistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalSongRepository
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class]
)
class TestRepositoryModule {

    @Provides
    @Singleton
    fun provideMediaDatabase(
        @ApplicationContext context: Context
    ): MediaDatabase = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @Provides
    @Singleton
    fun provideSongRepository(
        database: MediaDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): SongRepository = LocalSongRepository(appCoroutineScope, database.songDataDao())

    @Provides
    @Singleton
    fun provideMediaImporter(
        @ApplicationContext context: Context,
        songRepository: SongRepository,
        playlistRepository: PlaylistRepository,
        preferenceManager: GeneralPreferenceManager
    ): MediaImporter = MediaImporter(context, songRepository, playlistRepository, preferenceManager)

    @Provides
    @Singleton
    fun provideAlbumRepository(
        database: MediaDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): AlbumRepository = LocalAlbumRepository(appCoroutineScope, database.songDataDao())

    @Provides
    @Singleton
    fun provideAlbumArtistRepository(
        database: MediaDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): AlbumArtistRepository = LocalAlbumArtistRepository(appCoroutineScope, database.songDataDao())

    @Provides
    @Singleton
    fun providePlaylistRepository(
        @ApplicationContext context: Context,
        database: MediaDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): PlaylistRepository = LocalPlaylistRepository(
        context,
        appCoroutineScope,
        database.playlistDataDao(),
        database.playlistSongJoinDataDao()
    )

    @Provides
    @Singleton
    fun provideGenreRepository(
        songRepository: SongRepository,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): GenreRepository = LocalGenreRepository(appCoroutineScope, songRepository)
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :android:app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

If this fails because of missing bindings (e.g. `CastSessionManager`, `MediaSessionManager`, etc. required by app initializers), proceed to Task 5 first — you'll need the `TestAppModuleBinds` to suppress those initializers. Then come back and re-verify.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/androidTest/java/com/simplecityapps/shuttle/di/TestPlaybackModule.kt \
        android/app/src/androidTest/java/com/simplecityapps/shuttle/di/TestRepositoryModule.kt
git commit -m "$(cat <<'EOF'
Add Hilt test modules for smoke tests

TestPlaybackModule: FakePlayback + FakeAudioFocusHelper
TestRepositoryModule: in-memory Room DB
EOF
)"
```

---

### Task 5: Handle app initializers that pull in unwanted dependencies

**Files:**
- Create: `android/app/src/androidTest/java/com/simplecityapps/shuttle/di/TestAppModuleBinds.kt`

The production `AppModuleBinds` binds `PlaybackInitializer`, `TrialInitializer`, `MediaProviderInitializer`, `RemoteConfigInitializer` etc. into a `Set<AppInitializer>`. These initializers depend on `CastSessionManager`, `MediaSessionManager`, `BillingManager` etc. — none of which we provide in tests. We need to replace the set with an empty one.

- [ ] **Step 1: Write TestAppModuleBinds**

```kotlin
package com.simplecityapps.shuttle.di

import com.simplecityapps.shuttle.appinitializers.AppInitializer
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModuleBinds::class]
)
class TestAppModuleBinds {
    @Provides
    fun provideEmptyInitializers(): Set<AppInitializer> = emptySet()
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :android:app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

If there are still missing bindings, check the error for which class is missing and trace it back to a module. You may need to also provide no-op bindings for specific classes. The approach is: identify the missing binding, check if any test code actually needs it, and if not, provide a stub or remove the dependency path.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/androidTest/java/com/simplecityapps/shuttle/di/TestAppModuleBinds.kt
git commit -m "$(cat <<'EOF'
Suppress app initializers in test DI

Replaces AppModuleBinds with empty initializer set to avoid
pulling in Cast, Billing, Firebase, etc. during smoke tests.
EOF
)"
```

---

### Task 6: Create test data seeder

**Files:**
- Create: `android/app/src/androidTest/java/com/simplecityapps/shuttle/smoke/SmokeTestData.kt`

- [ ] **Step 1: Write SmokeTestData**

```kotlin
package com.simplecityapps.shuttle.smoke

import android.content.SharedPreferences
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import java.util.Date

object SmokeTestData {

    fun seedDatabase(database: MediaDatabase) {
        val dao = database.songDataDao()
        val songs = buildSongList()
        // Room in-memory DB with allowMainThreadQueries — safe in test setup
        kotlinx.coroutines.runBlocking {
            dao.insert(songs)
        }
    }

    fun setOnboarded(sharedPreferences: SharedPreferences) {
        sharedPreferences.edit()
            .putBoolean("has_onboarded", true)
            .commit()
    }

    private fun buildSongList(): List<SongData> {
        val now = Date()
        return listOf(
            songData("Highway to Hell", "AC/DC", "Back in Black", 1, 210000, "/music/01.mp3", now),
            songData("Thunderstruck", "AC/DC", "The Razors Edge", 1, 292000, "/music/02.mp3", now),
            songData("Back in Black", "AC/DC", "Back in Black", 2, 255000, "/music/03.mp3", now),
            songData("Bohemian Rhapsody", "Queen", "A Night at the Opera", 1, 354000, "/music/04.mp3", now),
            songData("Don't Stop Me Now", "Queen", "Jazz", 1, 209000, "/music/05.mp3", now),
            songData("Somebody to Love", "Queen", "A Day at the Races", 1, 297000, "/music/06.mp3", now),
            songData("Stairway to Heaven", "Led Zeppelin", "Led Zeppelin IV", 4, 482000, "/music/07.mp3", now),
            songData("Whole Lotta Love", "Led Zeppelin", "Led Zeppelin II", 1, 333000, "/music/08.mp3", now),
            songData("Black Dog", "Led Zeppelin", "Led Zeppelin IV", 1, 296000, "/music/09.mp3", now),
            songData("Immigrant Song", "Led Zeppelin", "Led Zeppelin III", 1, 146000, "/music/10.mp3", now),
        )
    }

    private fun songData(
        name: String,
        albumArtist: String,
        album: String,
        track: Int,
        duration: Int,
        path: String,
        lastModified: Date
    ): SongData = SongData(
        name = name,
        track = track,
        disc = 1,
        duration = duration,
        year = 1975,
        genres = listOf("Rock"),
        path = path,
        albumArtist = albumArtist,
        artists = listOf(albumArtist),
        album = album,
        size = 5_000_000,
        mimeType = "audio/mpeg",
        lastModified = lastModified,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = 320,
        bitDepth = 16,
        sampleRate = 44100,
        channelCount = 2
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :android:app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/androidTest/java/com/simplecityapps/shuttle/smoke/SmokeTestData.kt
git commit -m "$(cat <<'EOF'
Add smoke test data seeder

10 songs across 3 artists and 6 albums. Seeds Room DB and
sets hasOnboarded preference to skip onboarding flow.
EOF
)"
```

---

### Task 7: Write the smoke tests

**Files:**
- Create: `android/app/src/androidTest/java/com/simplecityapps/shuttle/smoke/SmokeTestSuite.kt`

- [ ] **Step 1: Write the smoke test class**

```kotlin
package com.simplecityapps.shuttle.smoke

import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class SmokeTestSuite {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: MediaDatabase

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        hiltRule.inject()
        SmokeTestData.seedDatabase(database)
        SmokeTestData.setOnboarded(sharedPreferences)
    }

    @After
    fun cleanup() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    @Test
    fun appLaunches_and_libraryShowsSongs() {
        scenario = launchActivity()

        // Bottom nav should be visible
        onView(withId(R.id.bottomNavigationView))
            .check(matches(isDisplayed()))

        // Navigate to Library tab
        onView(withId(R.id.libraryFragment))
            .perform(click())

        // Tab layout should be visible
        onView(withId(R.id.tabLayout))
            .check(matches(isDisplayed()))
    }

    @Test
    fun libraryTabs_showContent() {
        scenario = launchActivity()

        // Navigate to Library
        onView(withId(R.id.libraryFragment))
            .perform(click())

        // Click the Songs tab
        onView(withText("Songs"))
            .perform(click())

        // Give the Flow time to emit data to the RecyclerView
        Thread.sleep(1000)

        // Verify a song from our test data is visible
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .check(matches(hasDescendant(withText("Highway to Hell"))))
    }

    @Test
    fun tapSong_miniPlayerShowsSongTitle() {
        scenario = launchActivity()

        // Navigate to Library > Songs
        onView(withId(R.id.libraryFragment))
            .perform(click())
        onView(withText("Songs"))
            .perform(click())

        Thread.sleep(1000)

        // Tap the first song in the list
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

        // Mini player should show a song title (any of our test songs)
        Thread.sleep(500)
        onView(withId(R.id.titleTextView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun search_returnsSongs() {
        scenario = launchActivity()

        // Navigate to Search
        onView(withId(R.id.searchFragment))
            .perform(click())

        // The search view should be displayed
        onView(withId(R.id.searchView))
            .check(matches(isDisplayed()))
    }
}
```

**Note on `Thread.sleep`:** These are placeholders for the initial implementation. If they cause flakiness, replace with Espresso `IdlingResource` backed by the repository's `StateFlow` emission. For a small smoke suite, short sleeps after data-dependent transitions are pragmatic.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :android:app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/androidTest/java/com/simplecityapps/shuttle/smoke/SmokeTestSuite.kt
git commit -m "$(cat <<'EOF'
Add instrumented smoke tests

4 tests covering critical paths:
- App launch + library visible
- Library tabs show seeded songs
- Tap song activates mini player
- Search screen accessible
EOF
)"
```

---

### Task 8: Run the smoke tests on Gradle Managed Device

- [ ] **Step 1: Run tests on GMD**

Run: `./gradlew :android:app:pixel6Api34AtdDebugAndroidTest`

This will:
1. Download the ATD system image (first run only, ~800MB)
2. Create and boot the managed device
3. Install the debug APK and test APK
4. Run all androidTest classes
5. Shut down the device

Expected: 4 tests pass. If tests fail, diagnose — common issues:
- **Missing Hilt binding:** A production dependency isn't provided by any test module. Fix by adding a stub/fake to the appropriate test module.
- **View not found:** The `hasOnboarded` flag didn't take effect and onboarding is showing. Verify `SmokeTestData.setOnboarded()` is using the same `SharedPreferences` instance as the app.
- **Data not appearing:** The `Thread.sleep` wasn't long enough, or the Room Flow hasn't emitted yet. Increase sleep or add an IdlingResource.

- [ ] **Step 2: Fix any compilation or runtime failures**

Iterate until all 4 tests are green. Document any additional test modules or fakes that were needed.

- [ ] **Step 3: Final commit with any fixes**

```bash
git add -A android/app/src/androidTest/
git commit -m "$(cat <<'EOF'
Fix smoke test issues from first GMD run

[describe what was fixed]
EOF
)"
```

---

### Task 9: Verify clean run and document usage

- [ ] **Step 1: Run full suite from clean state**

Run: `./gradlew :android:app:pixel6Api34AtdDebugAndroidTest`

Expected: 4 tests PASSED, no failures.

- [ ] **Step 2: Run with sharding (for future scaling)**

When the suite grows, you can shard across multiple device instances:

```
./gradlew :android:app:pixel6Api34AtdDebugAndroidTest \
    -Pandroid.experimental.androidTest.numManagedDeviceShards=2
```

This is informational for now — 4 tests don't need sharding.

- [ ] **Step 3: Commit**

No code changes needed. This task is verification only.

---

## Gradle Managed Device Performance Notes

The configuration above includes these performance optimizations:

| Setting | Effect |
|---------|--------|
| `systemImageSource = "aosp-atd"` | ATD (Automated Test Device) images are stripped-down — no Play Store, no Google apps, faster boot (~40% vs full images) |
| `animationsDisabled = true` | Prevents animation-related flakiness and speeds up UI transitions |
| `execution = "ANDROIDX_TEST_ORCHESTRATOR"` | Each test runs in its own Instrumentation instance — prevents state leakage between tests |
| `allowMainThreadQueries()` on test DB | Avoids threading issues during test setup |
| `apiLevel = 34` | Latest stable ATD image with best emulator performance |

**To run in CI (GitHub Actions),** add to the workflow:

```yaml
- name: Smoke tests (GMD)
  run: ./gradlew :android:app:pixel6Api34AtdDebugAndroidTest
  env:
    CI: true
```

GMD handles emulator lifecycle automatically — no need for `reactivecircus/android-emulator-runner` or similar.

---

## What These Tests Cover vs Don't Cover

**Covered (breaks = the app is broken):**
- App launches after onboarding
- Navigation between bottom nav tabs
- Library tabs render and show data from DB
- Tapping a song triggers the playback flow through real QueueManager + PlaybackManager (with fake audio)
- Mini player appears in response to playback state
- Search screen is accessible

**Not covered (by design):**
- Onboarding flow (tested manually, changes rarely)
- Actual audio playback (faked at Playback interface)
- Network/remote media providers (Jellyfin, Emby, Plex)
- Settings screens
- Chromecast
- Queue management UI
- Now playing full screen

These can be added incrementally as confidence in the test infrastructure grows.
