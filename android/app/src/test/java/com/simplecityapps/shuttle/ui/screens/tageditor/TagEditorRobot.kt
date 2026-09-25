package com.simplecityapps.shuttle.ui.screens.tageditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/**
 * Test robot for [TagEditorScreen]. It holds the state the way the view model does (typing edits a field, reset puts
 * it back), so a test can type and watch Save come on.
 */
class TagEditorRobot(private val rule: ComposeContentTestRule) {
    var navigatedUp = false
        private set
    var saved = false
        private set

    private var shown by mutableStateOf<TagEditorUiState>(TagEditorUiState.Unreadable)
    private var rendered = false

    /** Shows [uiState]; later calls swap the state on the same screen. */
    fun setState(uiState: TagEditorUiState) {
        shown = uiState
        if (rendered) {
            rule.waitForIdle()
            return
        }
        rendered = true
        rule.setContent {
            S2Theme {
                TagEditorScreen(
                    uiState = shown,
                    onNavigateUp = { navigatedUp = true },
                    onFieldChange = { field, text -> updateField(field) { it.copy(text = text) } },
                    onFieldReset = { field -> updateField(field) { it.copy(text = it.initial) } },
                    onSave = { saved = true },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun updateField(
        field: TagField,
        transform: (TagFieldState) -> TagFieldState,
    ) {
        val editing = shown as? TagEditorUiState.Editing ?: return
        shown = editing.copy(fields = editing.fields.map { if (it.field == field) transform(it) else it })
    }

    val currentState: TagEditorUiState get() = shown

    fun typeInto(
        field: TagField,
        text: String,
    ) {
        scrollToField(field)
        rule.onNodeWithTag(fieldTag(field)).performTextClearance()
        rule.onNodeWithTag(fieldTag(field)).performTextInput(text)
        rule.waitForIdle()
    }

    fun resetField(label: String) {
        rule.onNodeWithContentDescription("Reset $label").performClick()
        rule.waitForIdle()
    }

    fun clickSave() {
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
    }

    fun clickBack() {
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
    }

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
    }

    fun assertSaveEnabled(enabled: Boolean) {
        val save = rule.onNodeWithText("Save")
        if (enabled) save.assertIsEnabled() else save.assertIsNotEnabled()
    }

    fun assertFieldShown(field: TagField) {
        scrollToField(field)
        rule.onNodeWithTag(fieldTag(field)).assertIsDisplayed()
    }

    fun assertFieldNotShown(field: TagField) {
        check(rule.onAllNodes(hasTestTag(fieldTag(field))).fetchSemanticsNodes().isEmpty()) { "$field is shown" }
    }

    fun assertFieldText(
        field: TagField,
        text: String,
    ) {
        scrollToField(field)
        rule.onNodeWithTag(fieldTag(field)).assertTextContains(text)
    }

    fun assertTextDisplayed(
        text: String,
        substring: Boolean = false,
    ) {
        rule.onAllNodesWithText(text, substring = substring)[0].assertIsDisplayed()
    }

    fun assertTextNotDisplayed(text: String) {
        check(rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) { "\"$text\" is shown" }
    }

    private fun scrollToField(field: TagField) {
        rule.onNodeWithTag(fieldTag(field)).performScrollTo()
    }

    private fun fieldTag(field: TagField) = "tag-field-${field.name}"
}
