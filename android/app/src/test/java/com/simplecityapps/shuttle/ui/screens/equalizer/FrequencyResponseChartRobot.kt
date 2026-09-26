package com.simplecityapps.shuttle.ui.screens.equalizer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import kotlinx.collections.immutable.ImmutableList

/** Test robot for [FrequencyResponseChart] Compose characterisation tests. */
class FrequencyResponseChartRobot(private val rule: ComposeContentTestRule) {

    fun setContent(points: ImmutableList<FrequencyResponsePoint>) {
        rule.setContent {
            S2AppTheme(AppThemeState()) {
                FrequencyResponseChart(points = points)
            }
        }
    }

    fun assertChartDisplayed() {
        rule.onNodeWithTag("frequencyResponseChart").assertIsDisplayed()
    }

    fun assertTextDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertTextNotDisplayed(text: String) {
        rule.onNodeWithText(text).assertDoesNotExist()
    }
}
