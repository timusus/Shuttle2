package com.simplecityapps.shuttle.ui.shell.player.np410

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.SampleArtworkGlide
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.ui.shell.AppShell
import com.simplecityapps.shuttle.ui.shell.EmptyShellQueue
import com.simplecityapps.shuttle.ui.shell.RecordingPlayerActions
import com.simplecityapps.shuttle.ui.shell.fakeShellEntryProvider
import com.simplecityapps.shuttle.ui.shell.player.PlayerProgress
import com.simplecityapps.shuttle.ui.shell.sampleShellQueue
import com.simplecityapps.shuttle.ui.shell.windowInfo
import java.io.File
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the #410 Now Playing variants into `docs/design/np-sheet-410/`: each at rest and with Up
 * Next dragged up by 64 dp, over the shell's Home with a 24 dp status bar and a 24 dp
 * gesture bar. A no-op under plain `testDebugUnitTest`; record with
 * `./gradlew :android:app:recordRoborazziDebug --tests '*NowPlayingSheetVariantsScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NowPlayingSheetVariantsScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val variant = mutableStateOf(NowPlayingVariant.FullLevel)
    private val drag = mutableStateOf(0.dp)
    private val playerState = mutableStateOf(sampleQueue)
    private val actions = RecordingPlayerActions(playerState)

    @Before
    fun installSampleArtwork() = SampleArtworkGlide.install(ApplicationProvider.getApplicationContext())

    @After
    fun uninstallSampleArtwork() = SampleArtworkGlide.uninstall()

    private fun setContent(
        widthDp: Int,
        heightDp: Int,
    ) {
        composeTestRule.setContent {
            val currentVariant by variant
            val currentDrag by drag
            S2Theme {
                Box {
                    // An empty queue: the destinations and nav bar with no mini player, as the sheet covers it.
                    AppShell(
                        playerUi = EmptyShellQueue,
                        progress = { sampleProgress },
                        actions = actions,
                        snackbarHostState = remember { SnackbarHostState() },
                        windowAdaptiveInfo = windowInfo(widthDp, heightDp),
                        entryProvider = ::fakeShellEntryProvider,
                    )
                    NowPlayingVariantPreview(currentVariant, playerState.value, { sampleProgress }, actions, currentDrag)
                }
            }
        }
        applySystemBars()
    }

    /**
     * Robolectric draws no system bars: the content view replaces whatever insets reach it with a
     * 24 dp status bar and gesture bar, so every layout below reads real insets.
     */
    private fun applySystemBars() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            val bar = (SystemBarDp * activity.resources.displayMetrics.density).roundToInt()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, bar, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bar))
                .build()
            val content = activity.findViewById<View>(android.R.id.content)
            ViewCompat.setOnApplyWindowInsetsListener(content) { _, _ -> insets }
            ViewCompat.dispatchApplyWindowInsets(content, insets)
            content.requestApplyInsets()
        }
        composeTestRule.waitForIdle()
    }

    private fun shot(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = RoborazziOptions(captureType = RoborazziOptions.CaptureType.Screenshot()),
        )
    }

    private fun record(
        device: String,
        widthDp: Int,
        heightDp: Int,
        dragged: Boolean = true,
    ) {
        setContent(widthDp, heightDp)
        NowPlayingVariant.entries.forEach {
            variant.value = it
            drag.value = 0.dp
            shot("$device-${it.slug}-rest")
            if (dragged) {
                drag.value = DragDistance
                shot("$device-${it.slug}-dragging")
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phone() = record("phone", 411, 891)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun phoneShort() = record("phone-short", 360, 640)

    @Test
    @Config(qualifiers = "w411dp-h826dp-xhdpi")
    fun foldableFolded() = record("foldable-folded", 411, 826, dragged = false)

    private companion object {
        const val SystemBarDp = 24

        /** Early in the drag, while a content-height sheet's top edge is still rising towards the status bar. */
        val DragDistance = 64.dp

        val sampleQueue = sampleShellQueue(size = 16)
        val sampleProgress = PlayerProgress(positionMs = 60_000, durationMs = sampleQueue.current!!.durationMs.toLong())

        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/np-sheet-410")
        }
    }
}
