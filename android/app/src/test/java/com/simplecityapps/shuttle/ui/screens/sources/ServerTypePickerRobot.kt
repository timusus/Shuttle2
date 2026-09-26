package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.shuttle.model.MediaProviderType

class ServerTypePickerRobot(private val rule: ComposeContentTestRule) {
    val selected = mutableListOf<MediaProviderType>()
    var dismissed = 0

    @OptIn(ExperimentalMaterial3Api::class)
    fun setContent() {
        rule.setContent {
            ServerTypePickerSheet(
                onTypeSelected = { selected += it },
                onDismissRequest = { dismissed++ },
            )
        }
    }

    fun assertTextDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
    }
}
