package com.simplecityapps.shuttle.debug.livelog

import android.util.Log
import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Characterisation tests for [LiveLogScreen]. */
@RunWith(RobolectricTestRunner::class)
class LiveLogScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LiveLogRobot(composeTestRule)

    private val lines = listOf(
        LiveLogLine(id = 1, timestampMillis = 0, priority = Log.INFO, tag = "S2", message = "Playback started"),
        LiveLogLine(id = 2, timestampMillis = 1_000, priority = Log.ERROR, tag = "S2", message = "Failed to load art")
    )

    @Test
    fun `shows the empty state with nothing logged`() {
        robot.setContent(LiveLogUiState(lines = emptyList()))

        robot.assertDisplayed("Nothing logged yet")
    }

    @Test
    fun `lists each line's message`() {
        robot.setContent(LiveLogUiState(lines = lines))

        robot.assertDisplayed("Playback started")
        robot.assertDisplayed("Failed to load art")
    }

    @Test
    fun `clear invokes onClear`() {
        robot.setContent(LiveLogUiState(lines = lines))

        robot.tapClear()

        robot.clearCount shouldBe 1
    }
}
