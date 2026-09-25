package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import com.simplecityapps.trial.PaywallPlan

/** Test robot for [PaywallScreen]. */
class PaywallRobot(private val rule: ComposeContentTestRule) {
    val selectedPlans = mutableListOf<PaywallPlan>()
    var purchases = 0
        private set
    var restores = 0
        private set
    var retries = 0
        private set
    var manageTaps = 0
        private set
    var closed = false
        private set

    fun setContent(uiState: PaywallUiState) {
        rule.setContent {
            S2AppTheme(AppThemeState()) {
                PaywallScreen(
                    uiState = uiState,
                    onClose = { closed = true },
                    onSelectPlan = { selectedPlans += it },
                    onPurchase = { purchases++ },
                    onRestore = { restores++ },
                    onRetry = { retries++ },
                    onManageSubscription = { manageTaps++ }
                )
            }
        }
    }

    fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    fun tapText(text: String) {
        scrollTo(text)
        rule.onNode(hasText(text) and hasClickAction()).performClick()
    }

    /** Taps the plan card whose title is [title]. */
    fun tapPlan(title: String) {
        scrollTo(title)
        rule.onNodeWithText(title).performClick()
    }

    fun tapBack() {
        rule.onNodeWithContentDescription("Back").performClick()
    }

    fun assertDisplayed(text: String) {
        scrollTo(text)
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    /** For text every plan card shows, like a placeholder price. */
    fun assertShownOnEveryPlan(text: String) {
        rule.onAllNodesWithText(text).assertCountEquals(2).onFirst().assertIsDisplayed()
    }

    fun assertNotShown(text: String) {
        rule.onNodeWithText(text).assertDoesNotExist()
    }

    fun assertEnabled(text: String) {
        scrollTo(text)
        rule.onNode(hasText(text) and hasClickAction()).assertIsEnabled()
    }

    fun assertNotEnabled(text: String) {
        scrollTo(text)
        rule.onNode(hasText(text) and hasClickAction()).assertIsNotEnabled()
    }

    fun assertPlanSelected(title: String) {
        scrollTo(title)
        rule.onNode(hasText(title) and hasClickAction()).assertIsSelected()
    }

    fun assertPlanNotSelected(title: String) {
        scrollTo(title)
        rule.onNode(hasText(title) and hasClickAction()).assertIsNotSelected()
    }
}
