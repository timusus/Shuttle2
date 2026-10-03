package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var expanded by mutableStateOf(false)
    private var dismissals = 0

    /** A 600 dp scaffold whose panel, while [expanded], holds a [panelHeight] tall block. */
    private fun setScaffold(panelHeight: Int = 100) {
        composeTestRule.setContent {
            S2Theme {
                S2ExpandableSheetScaffold(
                    expanded = expanded,
                    bottomBar = { Text("Bar") },
                    expandedContent = {
                        S2PanelSheet(
                            onDismiss = {
                                dismissals++
                                expanded = false
                            },
                            modifier = Modifier.testTag(PANEL),
                        ) {
                            Box(Modifier.fillMaxWidth().height(panelHeight.dp)) { Text("Panel content") }
                        }
                    },
                    modifier = Modifier.size(width = 400.dp, height = 600.dp),
                ) {
                    Text("Song")
                }
            }
        }
    }

    @Test
    fun `an open panel shows its content, as tall as it needs`() {
        setScaffold(panelHeight = 100)
        expanded = true

        composeTestRule.onNodeWithText("Panel content").assertIsDisplayed()
        // The block and the grip, well short of the 60% cap.
        composeTestRule.onNodeWithTag(PANEL).getUnclippedBoundsInRoot().let { it.bottom - it.top } shouldBeLessThan 200.dp
    }

    @Test
    fun `a tall panel stops at the cap`() {
        setScaffold(panelHeight = 1_000)
        expanded = true

        composeTestRule.onNodeWithTag(PANEL).getUnclippedBoundsInRoot().let { it.bottom - it.top } shouldBeLessThan 400.dp
    }

    @Test
    fun `an accessibility service closes the panel from its grip`() {
        setScaffold()
        expanded = true

        composeTestRule.onNodeWithContentDescription("Close panel").performSemanticsAction(SemanticsActions.Dismiss)
        dismissals shouldBe 1
        expanded shouldBe false
    }

    @Test
    fun `dragging the grip down closes the panel`() {
        setScaffold()
        expanded = true

        composeTestRule.onNodeWithContentDescription("Close panel").performTouchInput { swipeDown(endY = bottom + 300f) }
        composeTestRule.waitForIdle()
        dismissals shouldBe 1
    }

    @Test
    fun `a panel reopened while it slides away comes back to its resting place`() {
        setScaffold()
        expanded = true
        composeTestRule.waitForIdle()
        // The grip, which moves with the sheet's drag offset.
        val resting = composeTestRule.onNodeWithContentDescription("Close panel").getUnclippedBoundsInRoot().top

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithContentDescription("Close panel").performTouchInput { swipeDown(endY = bottom + 300f) }
        composeTestRule.mainClock.advanceTimeByFrame()
        dismissals shouldBe 1
        expanded = true
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Close panel").getUnclippedBoundsInRoot().top shouldBe resting
    }

    private companion object {
        const val PANEL = "panel"
    }
}
