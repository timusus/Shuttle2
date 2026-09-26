package com.simplecityapps.snapshot

import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.inspectionMode
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.github.takahirom.roborazzi.toRoborazziComposeOptions
import com.simplecityapps.shuttle.ui.snapshot.Snapshot
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import sergio.sastre.composable.preview.scanner.core.preview.getAnnotation

/**
 * Screenshots every `@Snapshot`-annotated `@Preview` in the app with Roborazzi.
 *
 * `./gradlew :android:app:recordRoborazziDebug` records the goldens into src/test/snapshots/images;
 * `verifyRoborazziDebug` compares against them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnapshotComposePreviewTests(
    private val preview: ComposablePreview<AndroidPreviewInfo>
) {

    // Skip the whole render (#538) unless Roborazzi is recording or verifying;
    // verifyRoborazziDebug still renders every preview on every landing.
    @Before
    fun skipUnlessRoborazziActive() = assumeTrue(roborazziSystemPropertyTaskType().isEnabled())

    @Test
    fun previewTests() {
        val snapshot = requireNotNull(preview.getAnnotation<Snapshot>())
        preview.captureRoboImage(
            filePath = "${roborazziSystemPropertyOutputDirectory()}/${preview.screenshotName()}.png",
            // Linux AA tolerance: see the root build.gradle.kts (#458). Combined with, never
            // below, the preview's own tolerance.
            roborazziOptions = RoborazziOptions(
                captureType = RoborazziOptions.CaptureType.Screenshot(),
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = maxOf(
                        snapshot.maxPercentDifference.toFloat() / 100f,
                        System.getProperty("s2.roborazzi.changeThreshold")?.toFloat() ?: 0f
                    )
                )
            ),
            // Inspection mode, as in Android Studio: previews skip their image loads.
            roborazziComposeOptions = preview.toRoborazziComposeOptions().builder().inspectionMode(true).build()
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun previews(): List<ComposablePreview<AndroidPreviewInfo>> = AndroidComposablePreviewScanner()
            .scanPackageTrees("com.simplecityapps.shuttle.ui")
            .includePrivatePreviews()
            .includeAnnotationInfoForAllOf(Snapshot::class.java)
            .getPreviews()
            .filter { preview -> preview.getAnnotation<Snapshot>() != null }

        private fun ComposablePreview<AndroidPreviewInfo>.screenshotName(): String {
            val index = previewIndex?.let { "_$it" }.orEmpty()
            return "${declaringClass.substringAfterLast('.')}_$methodName$index"
        }
    }
}
