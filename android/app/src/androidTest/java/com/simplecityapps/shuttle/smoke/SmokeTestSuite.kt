package com.simplecityapps.shuttle.smoke

import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import androidx.test.rule.GrantPermissionRule
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.shuttle.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Launches the real app shell against a seeded library and walks its main paths: Home, the Library's Songs tab,
 * playing a song from the mini player, and search. The Maestro flows in support/maestro cover the same paths in depth.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class SmokeTestSuite {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @get:Rule(order = 2)
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        scenario = launchActivity()
    }

    @After
    fun cleanup() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    @Test
    fun appLaunches_toHome() {
        waitFor(labelled("Shuffle all"))
    }

    @Test
    fun libraryTabs_showContent() {
        openSongs()
    }

    @Test
    fun tapSong_startsPlaybackInMiniPlayer() {
        openSongs()
        composeRule.onAllNodes(hasText(SmokeTestData.FIRST_SONG)).onFirst().performClick()

        waitFor(labelled("Pause"))
        composeRule.onAllNodes(labelled("Pause")).onFirst().performClick()
        waitFor(labelled("Play"))
    }

    @Test
    fun search_typingQuery_showsResults() {
        composeRule.onAllNodes(labelled("Search")).onFirst().performClick()
        waitFor(hasSetTextAction())
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(SmokeTestData.ARTIST)
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(SmokeTestData.ARTIST)).fetchSemanticsNodes().size > 1
        }
    }

    private fun openSongs() {
        waitFor(labelled("Library"))
        composeRule.onAllNodes(labelled("Library")).onFirst().performClick()
        waitFor(hasText("Songs"))
        composeRule.onAllNodes(hasText("Songs")).onFirst().performClick()
        waitFor(hasText(SmokeTestData.FIRST_SONG))
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private fun labelled(label: String): SemanticsMatcher = hasText(label) or hasContentDescription(label)

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
