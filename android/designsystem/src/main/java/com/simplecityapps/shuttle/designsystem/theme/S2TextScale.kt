package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity

/**
 * From this font scale up (the system's 150% text and above), text needs the room: fixed-height chrome
 * keeps its primary line and drops the rest, and rows trade labels for icons.
 */
const val S2LargeTextFontScale = 1.5f

/** Whether the user's text size is [S2LargeTextFontScale] or more. */
@Composable
@ReadOnlyComposable
fun isLargeText(): Boolean = LocalDensity.current.fontScale >= S2LargeTextFontScale
