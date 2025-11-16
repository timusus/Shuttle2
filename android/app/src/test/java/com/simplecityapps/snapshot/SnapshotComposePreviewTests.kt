package com.simplecityapps.snapshot

import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@RunWith(TestParameterInjector::class)
class SnapshotComposePreviewTests(
    @param:TestParameter(valuesProvider = ComposePreviewProvider::class)
    val preview: ComposablePreview<AndroidPreviewInfo>
) {

    @get:Rule
    val paparazzi = PaparazziPreviewRule.createFor(preview)

    @Test
    fun previewTests() {
        paparazzi.snapshot { preview() }
    }
}
