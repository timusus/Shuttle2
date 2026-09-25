package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/** One action in an [S2ButtonGroup]. */
class S2GroupAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    /** Observes or drives the press state; the catalogue uses it to show the width morph. */
    val interactionSource: MutableInteractionSource? = null,
)

/**
 * The standard group behind a screen's main actions: [primary] is the one filled button (Play) and
 * [secondary] are tonal (Shuffle). Pressing a button widens it and squeezes its neighbours.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2ButtonGroup(
    primary: S2GroupAction,
    modifier: Modifier = Modifier,
    secondary: List<S2GroupAction> = emptyList(),
    size: S2ButtonSize = S2ButtonSize.Small,
) {
    val actions = listOf(primary) + secondary
    val ownSources = remember(actions.size) { List(actions.size) { MutableInteractionSource() } }
    val interactionSources = actions.mapIndexed { index, action -> action.interactionSource ?: ownSources[index] }
    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier,
    ) {
        actions.forEachIndexed { index, action ->
            customItem(
                buttonGroupContent = {
                    S2Button(
                        text = action.label,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f).animateWidth(interactionSources[index]),
                        style = if (index == 0) S2ButtonStyle.Filled else S2ButtonStyle.Tonal,
                        size = size,
                        icon = action.icon,
                        interactionSource = interactionSources[index],
                    )
                },
                menuContent = { state ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            action.onClick()
                            state.dismiss()
                        },
                    )
                },
            )
        }
    }
}

/**
 * A connected single-choice group (sort order, view mode): the options share one pill, the
 * selected one is checked, and pressing morphs its corners.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> S2ConnectedButtonGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
    enabled: Boolean = true,
    interactionSources: List<MutableInteractionSource>? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                enabled = enabled,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                interactionSource = interactionSources?.getOrNull(index),
            ) {
                icon?.let {
                    Icon(it(option), contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                Text(label(option), maxLines = 1)
            }
        }
    }
}

@Preview
@Composable
private fun S2ButtonGroupPreview() {
    S2Theme {
        S2ButtonGroup(
            primary = S2GroupAction("Play", {}, Icons.Rounded.PlayArrow),
            secondary = listOf(S2GroupAction("Shuffle", {}, Icons.Rounded.Shuffle)),
        )
    }
}
