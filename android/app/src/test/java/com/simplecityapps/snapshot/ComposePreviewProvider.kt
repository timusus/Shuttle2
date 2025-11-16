package com.simplecityapps.snapshot

import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import com.simplecityapps.shuttle.ui.snapshot.Snapshot
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import sergio.sastre.composable.preview.scanner.core.preview.getAnnotation

class ComposePreviewProvider : TestParameterValuesProvider() {

    override fun provideValues(context: Context?): List<ComposablePreview<AndroidPreviewInfo>> = AndroidComposablePreviewScanner()
        .scanPackageTrees("com.simplecityapps.shuttle.ui")
        .includePrivatePreviews()
        .includeAnnotationInfoForAllOf(Snapshot::class.java)
        .getPreviews()
        .filter { preview -> preview.getAnnotation<Snapshot>() != null }
}
