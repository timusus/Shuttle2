package com.simplecityapps.shuttle.ui.preview

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.simplecityapps.shuttle.R
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner

/**
 * A `@Preview` draws its sample cover the way Android Studio renders it: inspection mode on and no Glide installed, so
 * the cover can only come from [SampleArtwork]. Renders the song row's preview, checks the artwork holds a cover rather
 * than the flat placeholder, and records it into `docs/design/previews/` (record with
 * `./gradlew :android:app:recordRoborazziDebug --tests '*PreviewArtworkTest*'`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewArtworkTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun songRowPreviewDrawsItsSampleCover() {
        val preview = AndroidComposablePreviewScanner()
            .scanPackageTrees("com.simplecityapps.shuttle.ui.screens.library.songs")
            .includePrivatePreviews()
            .getPreviews()
            .first { it.methodName == "SongListItemPreview" && it.previewInfo.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { preview() }
        }

        val artwork = ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.artwork)
        val pixels = composeTestRule.onNodeWithContentDescription(artwork).captureToImage().toPixelMap()
        val colours = (0 until pixels.width).flatMap { x -> (0 until pixels.height).map { y -> pixels[x, y] } }.toSet()
        // The placeholder is a flat vector icon, a handful of colours; a generated cover has hundreds.
        colours.size shouldBeGreaterThan 200

        composeTestRule.onRoot().captureRoboImage(
            filePath = File(previewsDir, "SongListItemPreview.png").path,
            roborazziOptions = RoborazziOptions(captureType = RoborazziOptions.CaptureType.Screenshot()),
        )
    }

    private val previewsDir: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
            .resolve("docs/design/previews")
    }
}
