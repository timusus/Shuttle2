package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.simplecityapps.shuttle.designsystem.theme.ContinuousRoundedCornerShape

/** The corner of a [TonalIconContainer], as a share of its side, so every size reads as the same shape. */
private const val IconContainerCornerPercent = 30

/**
 * An [icon] centred in a continuous rounded square of [containerColor]: the settings rows' leading
 * icons and the empty and error states' illustrations.
 */
@Composable
internal fun TonalIconContainer(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, ContinuousRoundedCornerShape(IconContainerCornerPercent)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}
