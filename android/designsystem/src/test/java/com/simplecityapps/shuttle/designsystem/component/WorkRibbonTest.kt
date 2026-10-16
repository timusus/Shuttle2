package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkRibbonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val jumps = mutableListOf<Int>()
    private var listOpened = 0

    // 300 dp wide: the two long movements are 140 dp each, the 6 000 ms one about 6 dp.
    private fun setRibbon() {
        composeTestRule.setContent {
            S2Theme {
                S2WorkRibbon(
                    segments = listOf(300_000, 300_000, 6_000),
                    currentSegment = 0,
                    currentFill = { 0f },
                    caption = "I of III · 10:06 left in work",
                    onSegmentClick = { jumps += it },
                    onOpenList = { listOpened++ },
                    segmentLabel = { "Play movement ${it + 1}" },
                    openListLabel = "Show movements",
                    modifier = Modifier.width(300.dp),
                )
            }
        }
    }

    @Test
    fun `a wide segment jumps to its movement`() {
        setRibbon()
        composeTestRule.onNodeWithContentDescription("Play movement 2").performClick()
        jumps shouldBe listOf(1)
        listOpened shouldBe 0
    }

    @Test
    fun `a segment under 24 dp opens the movement list`() {
        setRibbon()
        composeTestRule.onNodeWithContentDescription("Show movements").performClick()
        jumps shouldBe emptyList()
        listOpened shouldBe 1
    }

    @Test
    fun `the caption always opens the movement list`() {
        setRibbon()
        composeTestRule.onNodeWithText("I of III · 10:06 left in work").performClick()
        jumps shouldBe emptyList()
        listOpened shouldBe 1
    }
}
