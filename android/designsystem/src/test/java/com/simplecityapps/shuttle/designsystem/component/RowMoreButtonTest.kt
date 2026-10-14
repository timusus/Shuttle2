package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class RowMoreButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a row, an inset row and a top result card centre their overflow glyph on the same keyline`() {
        composeTestRule.setContent {
            S2Theme {
                Column(Modifier.width(400.dp).testTag("stack")) {
                    AlbumRow(title = "Row", artist = "Artist", onClick = {}, onMore = {})
                    AlbumRow(
                        title = "Inset row",
                        artist = "Artist",
                        onClick = {},
                        modifier = Modifier.padding(horizontal = 8.dp),
                        onMore = {},
                        edgeInset = 8.dp,
                    )
                    SearchTopResult(
                        kind = "Album",
                        title = AnnotatedString("Card"),
                        subtitle = null,
                        playLabel = "Play",
                        playDescription = "Play card",
                        onClick = {},
                        onPlay = {},
                        modifier = Modifier.fillMaxWidth(),
                        onMore = {},
                        artwork = {},
                    )
                }
            }
        }
        val rootWidthPx = composeTestRule.onNodeWithTag("stack").fetchSemanticsNode().boundsInRoot.right
        val buttons = composeTestRule.onAllNodesWithContentDescription("More options")
        val distancesDp = (0 until 3).map { i ->
            val bounds = buttons[i].fetchSemanticsNode().boundsInRoot
            rootWidthPx - (bounds.left + bounds.right) / 2f
        }
        // mdpi: one px per dp
        val keyline = TrailingActionKeyline.value
        distancesDp.forEach { assertEquals(keyline, it, 0.5f) }
        (0 until 3).forEach { i ->
            val touch = buttons[i].fetchSemanticsNode().touchBoundsInRoot
            assertTrue(touch.width >= 48f && touch.height >= 48f)
        }
    }

    @Test
    fun `in right-to-left the glyph centre is on the keyline from the left edge`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                S2Theme {
                    Column(Modifier.width(400.dp)) {
                        AlbumRow(title = "Row", artist = "Artist", onClick = {}, onMore = {})
                    }
                }
            }
        }
        val bounds = composeTestRule.onNodeWithContentDescription("More options").fetchSemanticsNode().boundsInRoot
        assertEquals(TrailingActionKeyline.value, (bounds.left + bounds.right) / 2f, 0.5f)
    }
}
