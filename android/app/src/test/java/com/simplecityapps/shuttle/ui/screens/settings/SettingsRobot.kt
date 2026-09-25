package com.simplecityapps.shuttle.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme

/** Test robot for [SettingsRootScreen] and [SettingsDestinationScreen]. */
class SettingsRobot(private val rule: ComposeContentTestRule) {
    val openedDestinations = mutableListOf<SettingsDestination>()
    val switchChanges = mutableListOf<Pair<String, Boolean>>()
    val choiceSelections = mutableListOf<Pair<String, Int>>()
    val actions = mutableListOf<SettingsAction>()
    val openedLinks = mutableListOf<SettingsLink>()
    var navigatedUp = false
        private set

    fun setRootContent() {
        rule.setContent {
            S2AppTheme(AppThemeState()) {
                SettingsRootScreen(onNavigateUp = { navigatedUp = true }, onOpenDestination = { openedDestinations += it })
            }
        }
    }

    fun setDestinationContent(
        destination: SettingsDestination,
        uiState: SettingsUiState = SettingsUiState(),
        sdkInt: Int = 36,
        versionName: String? = null
    ) {
        rule.setContent {
            S2AppTheme(AppThemeState()) {
                SettingsDestinationScreen(
                    screen = SettingsCatalog.screen(destination),
                    uiState = uiState,
                    onNavigateUp = { navigatedUp = true },
                    onSwitchChange = { item, checked -> switchChanges += item.key to checked },
                    onChoiceSelect = { item, index -> choiceSelections += item.key to index },
                    onSliderChange = { _, _ -> },
                    onAction = { actions += it },
                    onOpenLink = { openedLinks += it },
                    sdkInt = sdkInt,
                    versionName = versionName
                )
            }
        }
    }

    /** Renders [destination] against a real [SettingsViewModel], the way the shell's entry does. */
    fun setDestinationContentWithViewModel(
        destination: SettingsDestination,
        viewModel: SettingsViewModel
    ) {
        rule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            S2AppTheme(AppThemeState()) {
                SettingsDestinationScreen(
                    screen = SettingsCatalog.screen(destination),
                    uiState = uiState,
                    onNavigateUp = { navigatedUp = true },
                    onSwitchChange = viewModel::onSwitchChange,
                    onChoiceSelect = viewModel::onChoiceSelect,
                    onSliderChange = viewModel::onSliderChange,
                    onAction = viewModel::onAction,
                    onOpenLink = { openedLinks += it },
                    sdkInt = 36
                )
            }
        }
    }

    fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    fun tapText(text: String) {
        scrollTo(text)
        rule.onNodeWithText(text).performClick()
    }

    /** Taps [text] in a dialog; dialogs sit outside the screen's scrolling list. */
    fun tapDialogText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    fun tapBack() {
        rule.onNodeWithContentDescription("Back").performClick()
    }

    fun assertDisplayed(text: String) {
        scrollTo(text)
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertDialogDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertNotShown(text: String) {
        rule.onNodeWithText(text).assertDoesNotExist()
    }

    fun assertSwitchOn(title: String) {
        scrollTo(title)
        rule.onNode(hasText(title) and hasClickAction(), useUnmergedTree = false).assertIsOn()
    }

    fun assertSwitchOff(title: String) {
        scrollTo(title)
        rule.onNode(hasText(title) and hasClickAction(), useUnmergedTree = false).assertIsOff()
    }

    fun assertEnabled(title: String) {
        scrollTo(title)
        rule.onNode(hasText(title) and hasClickAction()).assertIsEnabled()
    }

    fun assertNotEnabled(title: String) {
        scrollTo(title)
        rule.onNode(hasText(title) and hasClickAction()).assertIsNotEnabled()
    }
}
