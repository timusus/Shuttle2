package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R

/** Distance from the screen edge to the centre of a trailing overflow glyph: M3's 16dp end padding plus half the 24dp icon, as in a top app bar action. */
internal val TrailingActionKeyline = 28.dp

/** A row's ⋮ button, offset past the 16dp trailing padding so its glyph sits on [TrailingActionKeyline]; [edgeInset] is the container's inset from the screen edge. */
@Composable
internal fun RowMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    edgeInset: Dp = 0.dp,
) {
    S2IconButton(
        icon = Icons.Rounded.MoreVert,
        contentDescription = stringResource(R.string.ds_more_options),
        onClick = onClick,
        modifier = modifier.offset(x = 8.dp + edgeInset),
        enabled = enabled,
    )
}
