package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

enum class S2IconButtonStyle { Standard, Filled, Tonal, Outlined }

/** The M3 Expressive icon button sizes S2 uses, each a container size and its icon size. */
enum class S2IconButtonSize {
    ExtraSmall,
    Small,
    Medium,
    Large,
    ;

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    internal val containerSize: DpSize
        @Composable get() = when (this) {
            ExtraSmall -> IconButtonDefaults.extraSmallContainerSize()
            Small -> IconButtonDefaults.smallContainerSize()
            Medium -> IconButtonDefaults.mediumContainerSize()
            Large -> IconButtonDefaults.largeContainerSize()
        }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    internal val iconSize: Dp
        get() = when (this) {
            ExtraSmall -> IconButtonDefaults.extraSmallIconSize
            Small -> IconButtonDefaults.smallIconSize
            Medium -> IconButtonDefaults.mediumIconSize
            Large -> IconButtonDefaults.largeIconSize
        }
}

/** An M3 icon button with the Expressive press morph. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2IconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: S2IconButtonStyle = S2IconButtonStyle.Standard,
    size: S2IconButtonSize = S2IconButtonSize.Small,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val sizedModifier = modifier.size(size.containerSize)
    val shapes = IconButtonDefaults.shapes()
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription, Modifier.size(size.iconSize))
    }
    when (style) {
        S2IconButtonStyle.Standard -> IconButton(onClick, shapes, sizedModifier, enabled, interactionSource = interactionSource, content = content)
        S2IconButtonStyle.Filled -> FilledIconButton(onClick, shapes, sizedModifier, enabled, interactionSource = interactionSource, content = content)
        S2IconButtonStyle.Tonal -> FilledTonalIconButton(onClick, shapes, sizedModifier, enabled, interactionSource = interactionSource, content = content)
        S2IconButtonStyle.Outlined -> OutlinedIconButton(onClick, shapes, sizedModifier, enabled, interactionSource = interactionSource, content = content)
    }
}

/**
 * A two-state icon button (favourite, shuffle, repeat): checked morphs the shape to square and
 * fills it, and [checkedIcon] replaces [icon] when set.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2IconToggleButton(
    icon: ImageVector,
    contentDescription: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedIcon: ImageVector = icon,
    style: S2IconButtonStyle = S2IconButtonStyle.Standard,
    size: S2IconButtonSize = S2IconButtonSize.Small,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val sizedModifier = modifier.size(size.containerSize)
    val shapes = IconButtonDefaults.toggleableShapes()
    val content: @Composable () -> Unit = {
        Icon(if (checked) checkedIcon else icon, contentDescription, Modifier.size(size.iconSize))
    }
    when (style) {
        S2IconButtonStyle.Standard -> IconToggleButton(checked, onCheckedChange, shapes, sizedModifier, enabled, interactionSource = interactionSource, content = content)
        S2IconButtonStyle.Filled -> FilledIconToggleButton(checked, onCheckedChange, shapes, sizedModifier, enabled, interactionSource = interactionSource, content = content)
        S2IconButtonStyle.Tonal -> FilledTonalIconToggleButton(checked, onCheckedChange, shapes, sizedModifier, enabled, interactionSource = interactionSource, content = content)
        S2IconButtonStyle.Outlined -> OutlinedIconToggleButton(checked, onCheckedChange, shapes, sizedModifier, enabled, interactionSource = interactionSource, content = content)
    }
}

@Preview
@Composable
private fun S2IconToggleButtonPreview() {
    S2Theme {
        S2IconToggleButton(
            icon = Icons.Rounded.FavoriteBorder,
            checkedIcon = Icons.Rounded.Favorite,
            contentDescription = "Favourite",
            checked = true,
            onCheckedChange = {},
            style = S2IconButtonStyle.Tonal,
        )
    }
}
