package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * S2's spacing scale, the same steps as the iOS app's `Spacing` (ios/S2/Theme/Spacing.swift), so the two lay out
 * alike. Screens space and pad with these rather than literal dps.
 */
object S2Spacing {
    /** Between a title and its subtitle. */
    val tiny = 2.dp

    val xsmall = 4.dp

    /** Between tiles in a grid, and inside a compact cell. */
    val small = 8.dp

    val smallMedium = 12.dp

    /** A screen's horizontal margin on a phone, and a card's padding. */
    val medium = 16.dp

    /** Between a screen's sections. */
    val large = 24.dp

    val xlarge = 32.dp
}
