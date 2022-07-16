package com.simplecityapps.shuttle.compose.ui.components.mediaimporter

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
fun Int.toDp(): Dp {
    return with(LocalDensity.current) { toDp() }
}