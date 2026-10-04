package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.mediaprovider.Progress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A rescan over a library that already has items keeps the list, under a bar, where it was scrolled to (#625). */
@RunWith(RobolectricTestRunner::class)
class LibraryContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryContentRobot(composeTestRule)

    private val rows = (0 until 100).map { "Row $it" }

    @Test
    fun `a scan over existing items keeps the list under the scan bar`() {
        robot.setContent(LibraryContentState.Scanning, rows, Progress(1, 4))

        robot.assertListDisplayed()
        robot.assertTextDisplayed("Row 0")
        robot.assertScanBarDisplayed()
    }

    @Test
    fun `a scan with no items yet shows the scan placeholder instead of the list`() {
        robot.setContent(LibraryContentState.Scanning, emptyList(), Progress(1, 4))

        robot.assertListNotDisplayed()
        robot.assertScanPlaceholderDisplayed()
    }

    @Test
    fun `a ready list shows no scan bar`() {
        robot.setContent(LibraryContentState.Ready, rows)

        robot.assertListDisplayed()
        robot.assertScanBarNotDisplayed()
    }

    @Test
    fun `a scan starting and finishing keeps the list's scroll position`() {
        robot.setContent(LibraryContentState.Ready, rows)
        robot.scrollToIndex(60)
        robot.assertTextDisplayed("Row 60")

        robot.changeState(LibraryContentState.Scanning, Progress(1, 4))
        robot.assertTextDisplayed("Row 60")
        robot.assertTextNotDisplayed("Row 0")
        robot.assertScanBarDisplayed()

        robot.changeState(LibraryContentState.Ready)
        robot.assertTextDisplayed("Row 60")
        robot.assertTextNotDisplayed("Row 0")
        robot.assertScanBarNotDisplayed()
    }

    @Test
    fun `a scan whose total is not known yet shows the bar without a fraction`() {
        robot.setContent(LibraryContentState.Scanning, rows, Progress(0, 0))

        robot.assertListDisplayed()
        robot.assertScanBarDisplayed()
    }
}
