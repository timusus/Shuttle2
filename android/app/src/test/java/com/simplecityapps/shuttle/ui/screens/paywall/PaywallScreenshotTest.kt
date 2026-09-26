package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the S2 Pro paywall at phone size into `docs/design/paywall/` for review (#380). A no-op
 * under plain `testDebugUnitTest`; record with
 * `./gradlew :android:app:recordRoborazziDebug --tests '*PaywallScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class PaywallScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(
        name: String,
        uiState: PaywallUiState
    ) {
        composeTestRule.setContent {
            S2AppTheme(AppThemeState(theme = ThemeMode.Light)) {
                PaywallScreen(
                    uiState = uiState,
                    onClose = {},
                    onSelectPlan = {},
                    onPurchase = {},
                    onRestore = {},
                    onRetry = {},
                    onManageSubscription = {},
                    onStartTrial = {},
                    onOpenPrivacyPolicy = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = DocsDesignRoborazziOptions
        )
    }

    @Test
    fun free() = shot("free", PaywallScenarios.free)

    @Test
    fun trial() = shot("trial", PaywallScenarios.trial)

    @Test
    fun pro() = shot("pro", PaywallScenarios.subscriber)

    @Test
    fun pricesUnavailable() = shot("prices-unavailable", PaywallScenarios.pricesUnavailable)

    private companion object {
        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/paywall")
        }
    }
}
