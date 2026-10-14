package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.shuttle.model.MediaProviderType

/** Hosts the flow the way [ServerTypePickerRoute] does, with a stand-in for the sign-in dialog. */
class ServerTypePickerFlowRobot(private val rule: ComposeContentTestRule) {
    var dismissed = 0

    fun setContent() {
        rule.setContent {
            var signingIn by remember { mutableStateOf<MediaProviderType?>(null) }
            var open by remember { mutableStateOf(true) }
            if (open) {
                ServerTypePickerFlow(
                    signingIn = signingIn,
                    onTypeSelected = { type ->
                        signingIn = type
                        true
                    },
                    onDismissRequest = {
                        dismissed++
                        open = false
                    },
                    signIn = { type -> Text("Signing in to ${type.name}") },
                )
            }
        }
    }

    fun assertTextDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
    }
}
