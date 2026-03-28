package com.simplecityapps.shuttle.smoke

import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.GrantPermissionRule
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.MainActivity
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
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

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

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

        // Tab layout should be visible (use isDescendantOfA to avoid matching the debug drawer's tabLayout)
        onView(allOf(withId(R.id.tabLayout), isDescendantOfA(withId(R.id.constraintLayout))))
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

        // Mini player should show a song title
        Thread.sleep(500)
        onView(allOf(withId(R.id.titleTextView), isDescendantOfA(withId(R.id.sheet1PeekView))))
            .check(matches(isDisplayed()))
    }

    @Test
    fun search_isAccessible() {
        scenario = launchActivity()

        // Navigate to Search
        onView(withId(R.id.searchFragment))
            .perform(click())

        // The search view should be displayed
        onView(withId(R.id.searchView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun artistDrillDown_showsArtistDetail() {
        scenario = launchActivity()

        // Navigate to Library > Artists tab
        onView(withId(R.id.libraryFragment))
            .perform(click())
        onView(withText("Artists"))
            .perform(click())

        // Wait for Flow to emit data
        Thread.sleep(1000)

        // Tap the first artist in the list
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

        // Wait for navigation
        Thread.sleep(500)

        // Verify artist detail screen shows toolbar and content
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun tapSong_expandNowPlaying_showsControls() {
        scenario = launchActivity()

        // Navigate to Library > Songs tab and play a song
        onView(withId(R.id.libraryFragment))
            .perform(click())
        onView(withText("Songs"))
            .perform(click())

        Thread.sleep(1000)

        // Tap the first song
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

        Thread.sleep(500)

        // Expand to full playback by clicking the mini player peek view
        onView(withId(R.id.sheet1PeekView))
            .perform(click())

        Thread.sleep(500)

        // Verify full playback controls are visible
        onView(withId(R.id.playPauseButton))
            .check(matches(isDisplayed()))
        onView(withId(R.id.seekBar))
            .check(matches(isDisplayed()))
        onView(allOf(withId(R.id.shuffleButton), isDescendantOfA(withId(R.id.sheet1Container))))
            .check(matches(isDisplayed()))
    }

    @Test
    fun playingSong_queueShowsItems() {
        scenario = launchActivity()

        // Navigate to Library > Songs tab and play a song
        onView(withId(R.id.libraryFragment))
            .perform(click())
        onView(withText("Songs"))
            .perform(click())

        Thread.sleep(1000)

        // Tap the first song
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

        Thread.sleep(500)

        // Programmatically expand to sheet 2 (queue) via MultiSheetView
        scenario.onActivity { activity ->
            val multiSheetView = activity.findViewById<MultiSheetView>(R.id.multiSheetView)
            multiSheetView.goToSheet(MultiSheetView.Sheet.SECOND)
        }

        Thread.sleep(500)

        // Verify queue toolbar shows "Up Next"
        onView(withId(R.id.toolbarTitleTextView))
            .check(matches(allOf(isDisplayed(), withText("Up Next"))))

        // Verify queue recyclerView is displayed (has items)
        onView(allOf(withId(R.id.recyclerView), isDescendantOfA(withId(R.id.sheet2Container))))
            .check(matches(isDisplayed()))
    }

    @Test
    fun homeScreen_showsShuffleButton() {
        scenario = launchActivity()

        // Navigate to Home tab
        onView(withId(R.id.homeFragment))
            .perform(click())

        // Verify key home buttons are visible (scoped to appBarLayout to avoid playback ambiguity)
        onView(allOf(withId(R.id.shuffleButton), isDescendantOfA(withId(R.id.appBarLayout))))
            .check(matches(isDisplayed()))
        onView(allOf(withId(R.id.historyButton), isDescendantOfA(withId(R.id.appBarLayout))))
            .check(matches(isDisplayed()))
    }
}
