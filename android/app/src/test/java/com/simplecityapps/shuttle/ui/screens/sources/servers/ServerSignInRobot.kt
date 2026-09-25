package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput

/** The server sign-in form, rendered without its dialog window (see [ServerSignInForm]). */
class ServerSignInRobot(private val rule: ComposeContentTestRule) {
    var authenticated = 0
    var retried = 0
    var dismissed = 0
    val passwords = mutableListOf<String>()
    val authCodes = mutableListOf<String>()
    val rememberPassword = mutableListOf<Boolean>()

    fun setContent(uiState: ServerSignInUiState) {
        rule.setContent {
            ServerSignInForm(
                uiState,
                ServerSignInActions(
                    onAddressChange = {},
                    onUsernameChange = {},
                    onPasswordChange = { passwords += it },
                    onAuthCodeChange = { authCodes += it },
                    onRememberPasswordChange = { rememberPassword += it },
                    onAuthenticate = { authenticated++ },
                    onRetry = { retried++ },
                    onDismiss = { dismissed++ },
                ),
            )
        }
    }

    fun assertTextDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertTextNotDisplayed(text: String) {
        rule.onNodeWithText(text).assertDoesNotExist()
    }

    fun assertFieldCount(count: Int) {
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(count)
    }

    fun assertTextCount(text: String, count: Int) {
        rule.onAllNodesWithText(text).assertCountEquals(count)
    }

    fun typeInto(label: String, text: String) {
        field(label).performTextInput(text)
    }

    fun toggleRememberPassword() {
        rule.onNode(isToggleable()).performClick()
    }

    fun assertRevealPasswordShown(shown: Boolean) {
        val node = rule.onNodeWithContentDescription("Show password")
        if (shown) node.assertIsDisplayed() else node.assertDoesNotExist()
    }

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    fun assertAuthenticateEnabled(enabled: Boolean) {
        val button = rule.onNodeWithText("Authenticate")
        if (enabled) button.assertIsEnabled() else button.assertIsNotEnabled()
    }

    private fun field(label: String) = rule.onNode(hasSetTextAction() and hasText(label))
}
