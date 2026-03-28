package com.simplecityapps.shuttle.smoke

import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.GrantPermissionRule
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
class NavigationSmokeTest {

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

    /**
     * Navigates to all bottom nav destinations and all library tabs.
     */
    @Test
    fun bottomNavAndTabs() {
        scenario = launchActivity()

        // Home
        onView(withId(R.id.homeFragment)).perform(click())
        waitForView(allOf(withId(R.id.shuffleButton), isDescendantOfA(withId(R.id.appBarLayout))))

        // Library
        onView(withId(R.id.libraryFragment)).perform(click())
        waitForView(allOf(withId(R.id.tabLayout), isDescendantOfA(withId(R.id.constraintLayout))))

        // Search
        onView(withId(R.id.searchFragment)).perform(click())
        waitForView(withId(R.id.searchView))

        // Settings dialog
        onView(withId(R.id.bottomSheetFragment)).perform(click())
        waitForView(withText("Settings"))
        Espresso.pressBack()

        // Library tabs
        onView(withId(R.id.libraryFragment)).perform(click())

        onView(withText("Songs")).perform(click())
        waitForView(allOf(withId(R.id.recyclerView), hasDescendant(withText("Highway to Hell"))))

        onView(withText("Albums")).perform(click())
        waitForView(allOf(withId(R.id.recyclerView), hasDescendant(isDisplayed())))

        onView(withText("Artists")).perform(click())
        waitForView(allOf(withId(R.id.recyclerView), hasDescendant(isDisplayed())))

        onView(withText("Genres")).perform(click())
        // Genres uses Compose — just verify the tab didn't crash
        waitForView(allOf(withId(R.id.tabLayout), isDescendantOfA(withId(R.id.constraintLayout))))

        onView(withText("Playlists")).perform(click())
        waitForView(allOf(withId(R.id.recyclerView), isDisplayed()))
    }

    /**
     * Navigates to artist detail and album detail screens.
     */
    @Test
    fun detailScreens() {
        scenario = launchActivity()

        // Artist detail
        onView(withId(R.id.libraryFragment)).perform(click())
        onView(withText("Artists")).perform(click())
        waitForView(allOf(withId(R.id.recyclerView), hasDescendant(isDisplayed())))
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
        waitForView(allOf(withId(R.id.toolbar), isDescendantOfA(withId(R.id.collapsingToolbarLayout))))
        Espresso.pressBack()

        // Album detail — position 1 because position 0 is the shuffle header
        waitForView(withText("Albums"))
        onView(withText("Albums")).perform(click())
        waitForView(allOf(withId(R.id.recyclerView), hasDescendant(isDisplayed())))
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, click()))
        waitForView(allOf(withId(R.id.toolbar), isDescendantOfA(withId(R.id.collapsingToolbarLayout))))
    }

    /**
     * Navigates through playback screens: mini player, now playing, queue.
     */
    @Test
    fun playbackScreens() {
        scenario = launchActivity()

        // Play a song
        onView(withId(R.id.libraryFragment)).perform(click())
        onView(withText("Songs")).perform(click())
        waitForView(allOf(withId(R.id.recyclerView), hasDescendant(isDisplayed())))
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

        // Mini player
        waitForView(allOf(withId(R.id.titleTextView), isDescendantOfA(withId(R.id.sheet1PeekView))))

        // Now Playing
        onView(withId(R.id.sheet1PeekView)).perform(click())
        waitForView(allOf(withId(R.id.playPauseButton), isDescendantOfA(withId(R.id.sheet1Container)), isClickable()))

        // Queue — skipped here due to QueueFragment.onSlide auto-cleared-value crash
        // when goToSheet(SECOND) is called in this specific test sequence.
        // Queue navigation is covered by playingSong_queueShowsItems in SmokeTestSuite.
    }
}
