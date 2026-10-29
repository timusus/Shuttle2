package com.simplecityapps.shuttle.ui.common.mediaactions

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.actions.CopyContext
import com.simplecityapps.shuttle.ui.screens.songinfo.copyRows
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Records the "Play from…" sheet at phone size into `docs/design/mediaactions/` for review. A no-op under plain
 * `testDebugUnitTest`; record with `./gradlew :android:app:recordRoborazziDebug --tests '*CopyPickerSheetScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class CopyPickerSheetScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun skipUnlessRoborazziActive() = assumeTrue(roborazziSystemPropertyTaskType().isEnabled())

    @Test
    fun playFrom() {
        val local = createSong(id = 1, path = "/music/1.flac").copy(mimeType = "audio/flac", bitRate = 900)
        val emby = createSong(id = 2, path = "emby://2", mediaProvider = MediaProviderType.Emby).copy(mimeType = "audio/mpeg", bitRate = 320)
        val plex = createSong(id = 3, path = "plex://3", mediaProvider = MediaProviderType.Plex).copy(mimeType = "audio/mpeg", bitRate = 256)
        val rows = local.copy(copies = listOf(local, emby, plex)).copyRows(CopyContext())!!.rows
        composeTestRule.setContent {
            S2AppTheme(AppThemeState(theme = ThemeMode.Light)) {
                CopyPickerSheet(rows, onPick = {}, onDismissRequest = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "play-from.png").path,
            roborazziOptions = DocsDesignRoborazziOptions,
        )
    }

    companion object {
        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/mediaactions")
        }
    }
}
