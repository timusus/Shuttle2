package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records every catalogue board into `docs/design/catalog/<id>/` (see [catalogShots] for the
 * matrix). A no-op under plain `testDebugUnitTest`; `support/scripts/catalog` records, CI verifies.
 *
 * Boards render at their width class and their content height inside a window big enough for the
 * tallest. The clock is paused and stepped, so held presses settle and the indeterminate loading
 * indicator is caught at a fixed frame instead of animating forever.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h3000dp-mdpi")
class CatalogScreenshotTest(private val shot: CatalogShot) {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun board() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, shot.fontScale)) {
                CatalogTheme(
                    scheme = shot.scheme,
                    darkTheme = shot.darkTheme,
                    modifier = Modifier.width(shot.width.widthDp.dp).testTag(BOARD_TAG),
                ) {
                    shot.entry.board(shot.width)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeTestRule.onNodeWithTag(BOARD_TAG).captureRoboImage(
            filePath = "${roborazziSystemPropertyOutputDirectory()}/${shot.path}",
            roborazziOptions = RoborazziOptions(captureType = RoborazziOptions.CaptureType.Screenshot()),
        )
    }

    companion object {
        private const val BOARD_TAG = "catalog-board"
        private const val SETTLE_MILLIS = 1_000L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots(): List<CatalogShot> = catalogShots()
    }
}
