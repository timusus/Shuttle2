package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme

/** Test robot for [PaywallScreen]. */
class PaywallRobot(private val rule: ComposeContentTestRule) {
    var purchases = 0
        private set
    var restores = 0
        private set
    var redeemTaps = 0
        private set
    var retries = 0
        private set
    var manageTaps = 0
        private set
    var trialStarts = 0
        private set
    var privacyPolicyTaps = 0
        private set
    var closed = false
        private set

    fun setContent(uiState: PaywallUiState) {
        rule.setContent {
            S2AppTheme(AppThemeState()) {
                PaywallScreen(
                    uiState = uiState,
                    onClose = { closed = true },
                    onPurchase = { purchases++ },
                    onRestore = { restores++ },
                    onRedeemCode = { redeemTaps++ },
                    onRetry = { retries++ },
                    onManageSubscription = { manageTaps++ },
                    onStartTrial = { trialStarts++ },
                    onOpenPrivacyPolicy = { privacyPolicyTaps++ }
                )
            }
        }
    }

    /** Scrolls the list to [text]; the purchase buttons sit in a bar outside it, always in view. */
    fun scrollTo(text: String) {
        if (rule.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isEmpty() &&
            rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        ) {
            return
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    fun tapText(text: String) {
        scrollTo(text)
        rule.onNode(hasText(text) and hasClickAction()).performClick()
    }

    fun tapBack() {
        rule.onNodeWithContentDescription("Back").performClick()
    }

    fun assertDisplayed(text: String) {
        scrollTo(text)
        rule.onNodeWithText(text).assertIsDisplayed()
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
}
