package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** How often a held skip button repeats its seek, matching 1.0.10's `SkipButton`. */
internal const val SkipHoldRepeatIntervalMs = 500L

/**
 * A previous/next transport button that seeks in repeated steps while held instead of firing
 * [onClick] (1.0.10's `SkipButton`). Used in place of [S2IconButton] where [onHold] is non-null;
 * callers with no hold behaviour (the Audiobook/Podcast seek buttons) pass [onHold] as null and get
 * a plain tap-only [S2IconButton] instead.
 *
 * Built on [combinedClickable] (not a hand-rolled pointer-input detector) so it cooperates with an
 * ancestor's own clickable — this button sits inside the mini player's clickable [Surface], and only
 * `combinedClickable`'s shared gesture-detection lets the nested press win over the outer one the
 * same way a plain [S2IconButton] already does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun S2TransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHold: (() -> Unit)? = null,
    size: S2IconButtonSize = S2IconButtonSize.Small,
    interactionSource: MutableInteractionSource? = null,
) {
    if (onHold == null) {
        S2IconButton(icon, contentDescription, onClick, modifier, size = size, interactionSource = interactionSource)
        return
    }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(longPressTimeoutMillis)
        onHold()
        while (isActive) {
            delay(SkipHoldRepeatIntervalMs)
            onHold()
        }
    }
    Box(
        modifier = modifier
            .size(size.containerSize)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = source,
                indication = ripple(),
                // A touch hold is already seeking through the pressed timer above; with no press in
                // progress this is the accessibility long-click action, so seek one step.
                onLongClick = { if (!pressed) onHold() },
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size.iconSize))
    }
}
