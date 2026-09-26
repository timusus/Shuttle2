package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme

/** Test robot for [EqualizerScreen]. */
class EqualizerRobot(private val rule: ComposeContentTestRule) {
    val enabledChanges = mutableListOf<Boolean>()
    val presetsSelected = mutableListOf<Equalizer.Presets.Preset>()
    val bandChanges = mutableListOf<Pair<Int, Float>>()
    var bandChangesFinished = 0
        private set

    fun setContent(uiState: EqualizerUiState) {
        rule.setContent {
            S2AppTheme(AppThemeState()) {
                EqualizerScreen(
                    uiState = uiState,
                    onNavigateUp = {},
                    onEnabledChange = { enabledChanges += it },
                    onPresetSelect = { presetsSelected += it },
                    onBandGainChange = { frequency, gain -> bandChanges += frequency to gain },
                    onBandGainChangeFinished = { bandChangesFinished++ }
                )
            }
        }
    }

    fun setContentWithViewModel(viewModel: EqualizerViewModel) {
        rule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            S2AppTheme(AppThemeState()) {
                EqualizerScreen(
                    uiState = uiState,
                    onNavigateUp = {},
                    onEnabledChange = viewModel::onEnabledChange,
                    onPresetSelect = viewModel::onPresetSelect,
                    onBandGainChange = viewModel::onBandGainChange,
                    onBandGainChangeFinished = viewModel::onBandGainChangeFinished
                )
            }
        }
    }

    fun tapText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    /** Sets the band labelled [frequency] (e.g. "1k") to [gainDb], as a finished drag. */
    fun setBand(
        frequency: String,
        gainDb: Float
    ) {
        rule.onNodeWithContentDescription(frequency).performSemanticsAction(SemanticsActions.SetProgress) { it(gainDb) }
    }

    fun assertDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertSwitchOn() {
        rule.onNode(hasText("Use equalizer") and hasClickAction()).assertIsOn()
    }

    fun assertSwitchOff() {
        rule.onNode(hasText("Use equalizer") and hasClickAction()).assertIsOff()
    }

    fun assertBandNotEnabled(frequency: String) {
        rule.onNodeWithContentDescription(frequency).assertIsNotEnabled()
    }

    fun assertChartDisplayed() {
        rule.onNodeWithTag("frequencyResponseChart").assertIsDisplayed()
    }
}
