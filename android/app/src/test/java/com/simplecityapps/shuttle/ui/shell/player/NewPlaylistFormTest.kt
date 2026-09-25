package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The new playlist dialog's form, rendered without its window (see [NewPlaylistForm]). */
@RunWith(RobolectricTestRunner::class)
class NewPlaylistFormTest {
    @get:Rule
    val rule = createComposeRule()

    private val created = mutableListOf<String>()
    private var dismissed = 0

    private fun setContent() {
        rule.setContent { NewPlaylistForm(onCreate = { created += it }, onDismiss = { dismissed++ }) }
    }

    @Test
    fun `Create waits for a name`() {
        setContent()
        rule.onNodeWithText("Create").assertIsNotEnabled()

        rule.onNode(hasSetTextAction()).performTextInput("   ")
        rule.onNodeWithText("Create").assertIsNotEnabled()
    }

    @Test
    fun `Create sends the trimmed name and closes the dialog`() {
        setContent()
        rule.onNode(hasSetTextAction()).performTextInput(" Mixtape ")
        rule.onNodeWithText("Create").assertIsEnabled().performClick()

        created shouldBe listOf("Mixtape")
        dismissed shouldBe 1
    }

    @Test
    fun `Cancel closes it without creating anything`() {
        setContent()
        rule.onNode(hasSetTextAction()).performTextInput("Mixtape")
        rule.onNodeWithText("Cancel").performClick()

        created shouldBe emptyList()
        dismissed shouldBe 1
    }
}
