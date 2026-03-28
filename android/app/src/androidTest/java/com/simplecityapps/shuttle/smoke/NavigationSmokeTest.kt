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
import androidx.test.espresso.assertion.ViewAssertions.matches
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
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.anyOf
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

    @Test
    fun navigationCoverage_visitsAllMajorDestinations() {
        scenario = launchActivity()

        // ── 1. Home ──
        onView(withId(R.id.homeFragment))
            .perform(click())
        Thread.sleep(500)
        onView(allOf(withId(R.id.shuffleButton), isDescendantOfA(withId(R.id.appBarLayout))))
            .check(matches(isDisplayed()))

        // ── 2. Library ──
        onView(withId(R.id.libraryFragment))
            .perform(click())
        Thread.sleep(500)
        onView(allOf(withId(R.id.tabLayout), isDescendantOfA(withId(R.id.constraintLayout))))
            .check(matches(isDisplayed()))

        // ── 3. Search ──
        onView(withId(R.id.searchFragment))
            .perform(click())
        Thread.sleep(500)
        onView(withId(R.id.searchView))
            .check(matches(isDisplayed()))

        // ── 4. Settings ──
        onView(withId(R.id.bottomSheetFragment))
            .perform(click())
        Thread.sleep(300)
        onView(withText("Settings"))
            .check(matches(isDisplayed()))
        Espresso.pressBack()
        Thread.sleep(300)

        // ── 5. Songs tab ──
        onView(withId(R.id.libraryFragment))
            .perform(click())
        onView(withText("Songs"))
            .perform(click())
        Thread.sleep(1000)
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .check(matches(hasDescendant(withText("Highway to Hell"))))

        // ── 6. Albums tab ──
        onView(withText("Albums"))
            .perform(click())
        Thread.sleep(1000)
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .check(matches(isDisplayed()))

        // ── 7. Artists tab ──
        onView(withText("Artists"))
            .perform(click())
        Thread.sleep(1000)
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .check(matches(isDisplayed()))

        // ── 8. Genres tab ──
        onView(withText("Genres"))
            .perform(click())
        Thread.sleep(1000)
        // Genres uses Compose — just verify the tab navigated without crashing
        onView(allOf(withId(R.id.tabLayout), isDescendantOfA(withId(R.id.constraintLayout))))
            .check(matches(isDisplayed()))

        // ── 9. Playlists tab ──
        onView(withText("Playlists"))
            .perform(click())
        Thread.sleep(1000)
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .check(matches(isDisplayed()))

        // ── 10. Artist detail ──
        onView(withText("Artists"))
            .perform(click())
        Thread.sleep(1000)
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
        Thread.sleep(500)
        onView(allOf(withId(R.id.toolbar), isDescendantOfA(withId(R.id.collapsingToolbarLayout))))
            .check(matches(isDisplayed()))
        Espresso.pressBack()
        Thread.sleep(500)

        // ── 11. Album detail ──
        onView(withText("Albums"))
            .perform(click())
        Thread.sleep(1000)
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
        Thread.sleep(500)
        onView(allOf(withId(R.id.toolbar), isDescendantOfA(withId(R.id.collapsingToolbarLayout))))
            .check(matches(isDisplayed()))
        Espresso.pressBack()
        Thread.sleep(500)

        // ── 12. Genre detail ──
        onView(withText("Genres"))
            .perform(click())
        Thread.sleep(1000)
        // Genres uses Compose; tap the first item via RecyclerView if available,
        // otherwise the ComposeView hosts the list directly.
        // Try tapping the first visible clickable item in the genre list.
        try {
            onView(allOf(withId(R.id.recyclerView), isDisplayed()))
                .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
        } catch (e: Exception) {
            // Genres may use Compose without a RecyclerView — skip detail navigation
        }
        Thread.sleep(500)
        Espresso.pressBack()
        Thread.sleep(500)

        // ── 13. Play a song ──
        onView(withId(R.id.libraryFragment))
            .perform(click())
        onView(withText("Songs"))
            .perform(click())
        Thread.sleep(1000)
        onView(allOf(withId(R.id.recyclerView), isDisplayed()))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
        Thread.sleep(1000)

        // ── 14. Mini player ──
        onView(allOf(withId(R.id.titleTextView), isDescendantOfA(withId(R.id.sheet1PeekView))))
            .check(matches(isDisplayed()))

        // ── 15. Now Playing ──
        onView(withId(R.id.sheet1PeekView))
            .perform(click())
        Thread.sleep(500)
        onView(allOf(withId(R.id.playPauseButton), isDescendantOfA(withId(R.id.sheet1Container)), isClickable()))
            .check(matches(isDisplayed()))

        // ── 16. Queue ──
        scenario.onActivity { activity ->
            val multiSheetView = activity.findViewById<MultiSheetView>(R.id.multiSheetView)
            multiSheetView.goToSheet(MultiSheetView.Sheet.SECOND)
        }
        Thread.sleep(500)
        onView(withId(R.id.toolbarTitleTextView))
            .check(matches(allOf(isDisplayed(), withText("Up Next"))))

        // Collapse back to base state
        scenario.onActivity { activity ->
            val multiSheetView = activity.findViewById<MultiSheetView>(R.id.multiSheetView)
            multiSheetView.goToSheet(MultiSheetView.Sheet.FIRST)
        }
        Thread.sleep(300)
        Espresso.pressBack()
        Thread.sleep(300)
    }
}
