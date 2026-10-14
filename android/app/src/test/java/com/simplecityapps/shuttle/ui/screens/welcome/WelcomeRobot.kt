package com.simplecityapps.shuttle.ui.screens.welcome

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme

/** Hosts [WelcomeFlow] with the system permission prompt counted and a stand-in for the server picker. */
class WelcomeRobot(private val rule: ComposeContentTestRule) {
    var musicAccessRequests = 0
    var privacyOpened = 0

    fun setContent() {
        rule.setContent {
            S2AppTheme(AppThemeState(theme = ThemeMode.Light)) {
                WelcomeFlow(
                    onRequestMusicAccess = { musicAccessRequests++ },
                    onOpenPrivacy = { privacyOpened++ },
                    serverPicker = { onDismissRequest ->
                        TextButton(onClick = onDismissRequest) { Text(SERVER_PICKER) }
                    },
                )
            }
        }
    }

    fun assertTextDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertTextNotDisplayed(text: String) {
        rule.onNodeWithText(text).assertDoesNotExist()
    }

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    companion object {
        const val SERVER_PICKER = "Server picker"
    }
}
