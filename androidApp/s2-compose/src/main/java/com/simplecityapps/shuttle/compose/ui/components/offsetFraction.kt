package com.simplecityapps.shuttle.compose.ui.components

import androidx.compose.material.BottomSheetScaffoldState
import androidx.compose.material.ExperimentalMaterialApi

@OptIn(ExperimentalMaterialApi::class)
fun BottomSheetScaffoldState.offsetFraction(): Float {
    var offsetFraction = bottomSheetState.progress.fraction

    if (bottomSheetState.direction == 0f) {
        offsetFraction = if (bottomSheetState.isExpanded) 0f else 1f
    }

    if (bottomSheetState.direction < 0) {
        offsetFraction = 1f - offsetFraction
    }

    return offsetFraction
}