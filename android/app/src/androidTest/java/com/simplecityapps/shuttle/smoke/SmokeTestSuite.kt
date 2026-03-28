package com.simplecityapps.shuttle.smoke

import android.content.SharedPreferences
import androidx.recyclerview.widget.RecyclerView
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

        // Mini player should show a song title
        Thread.sleep(500)
        onView(withId(R.id.titleTextView))
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
}
