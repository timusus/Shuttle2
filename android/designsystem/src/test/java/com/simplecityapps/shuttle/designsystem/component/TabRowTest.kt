package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TabRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setTabRow(position: Float) {
        composeTestRule.setContent {
            S2Theme {
                S2TabRow(
                    labels = listOf("Songs", "Albums", "Artists"),
                    selectedIndex = 0,
                    onSelect = {},
                    indicatorPosition = { position },
                )
            }
        }
    }

    private fun indicatorLeftAndWidth(position: Float): Pair<Float, Float> {
        setTabRow(position)
        val bounds = composeTestRule.onNodeWithTag(TabIndicatorTestTag).getUnclippedBoundsInRoot()
        return bounds.left.value to bounds.width.value
    }

    // Tab nodes include label padding that is not symmetric about the text, so centres agree to a few dp.
    private companion object {
        const val CENTRE_TOLERANCE = 3f
    }

    @Test
    fun `the indicator sits halfway between two tabs at half a page offset`() {
        val (left, width) = indicatorLeftAndWidth(0.5f)
        val tabs = composeTestRule.onNodeWithText("Songs").getUnclippedBoundsInRoot() to
            composeTestRule.onNodeWithText("Albums").getUnclippedBoundsInRoot()
        val expectedCentre = (tabs.first.left.value + tabs.first.right.value + tabs.second.left.value + tabs.second.right.value) / 4
        assertEquals(expectedCentre, left + width / 2, CENTRE_TOLERANCE)
    }

    @Test
    fun `the indicator stays on the last tab when the position runs past it`() {
        val (left, width) = indicatorLeftAndWidth(5f)
        val last = composeTestRule.onNodeWithText("Artists").getUnclippedBoundsInRoot()
        assertEquals((last.left.value + last.right.value) / 2, left + width / 2, CENTRE_TOLERANCE)
        assertEquals(true, width > 0.dp.value)
    }
}
