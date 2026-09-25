package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/** Emphasis, highest first: one [Filled] action per screen, everything else below it. */
enum class S2ButtonStyle { Filled, Tonal, Outlined, Text }

/** The M3 Expressive button sizes S2 uses. Shape, padding, icon size and type follow the height. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
enum class S2ButtonSize(internal val height: Dp) {
    ExtraSmall(ButtonDefaults.ExtraSmallContainerHeight),
    Small(ButtonDefaults.MinHeight),
    Medium(ButtonDefaults.MediumContainerHeight),
    Large(ButtonDefaults.LargeContainerHeight),
}

/**
 * An M3 button at one of the Expressive sizes, with its round → square press morph. Pass an
 * [interactionSource] to observe or drive the interaction state (the catalogue shows pressed).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: S2ButtonStyle = S2ButtonStyle.Filled,
    size: S2ButtonSize = S2ButtonSize.Small,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val height = size.height
    val shapes = ButtonDefaults.shapesFor(height)
    val contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = icon != null)
    val sizedModifier = modifier.heightIn(min = height)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height)))
        }
        Text(text, style = ButtonDefaults.textStyleFor(height))
    }
    when (style) {
        S2ButtonStyle.Filled -> Button(
            onClick = onClick,
            shapes = shapes,
            modifier = sizedModifier,
            enabled = enabled,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )

        S2ButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick,
            shapes = shapes,
            modifier = sizedModifier,
            enabled = enabled,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )

        S2ButtonStyle.Outlined -> OutlinedButton(
            onClick = onClick,
            shapes = shapes,
            modifier = sizedModifier,
            enabled = enabled,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )

        S2ButtonStyle.Text -> TextButton(
            onClick = onClick,
            shapes = shapes,
            modifier = sizedModifier,
            enabled = enabled,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Preview
@Composable
private fun S2ButtonPreview() {
    S2Theme {
        S2Button(text = "Play", onClick = {}, icon = Icons.Rounded.PlayArrow, size = S2ButtonSize.Medium)
    }
}
