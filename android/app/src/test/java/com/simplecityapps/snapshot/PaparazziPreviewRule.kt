package com.simplecityapps.snapshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.simplecityapps.shuttle.ui.snapshot.Snapshot
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import sergio.sastre.composable.preview.scanner.core.preview.getAnnotation

object PaparazziPreviewRule {
    fun createFor(preview: ComposablePreview<AndroidPreviewInfo>): Paparazzi {
        val snapshot = requireNotNull(preview.getAnnotation<Snapshot>())
        val orientation = preview.previewInfo.device
            .substringAfter("orientation=")
            .substringBefore(",")
        val screenOrientation = when (orientation) {
            "landscape" -> ScreenOrientation.LANDSCAPE
            else -> ScreenOrientation.PORTRAIT
        }
        val screenHeight = preview.previewInfo.heightDp.let { heightDp ->
            if (heightDp > 0) {
                (heightDp * (DeviceConfig.PIXEL_6.density.dpiValue / 160f)).toInt()
            } else {
                DeviceConfig.PIXEL_6.screenHeight
            }
        }
        return Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_6.copy(
                screenHeight = screenHeight,
                orientation = screenOrientation
            ),
            maxPercentDifference = snapshot.maxPercentDifference
        )
    }
}
