package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R

@Composable
fun SelectionMark(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        content()

        if (isSelected) {
            SelectionMarkOverlay()
        }
    }
}

@Composable
private fun SelectionMarkOverlay() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0, 0, 0, 112))
            .padding(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_baseline_check_24),
            contentDescription = stringResource(R.string.selection_mark),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}
