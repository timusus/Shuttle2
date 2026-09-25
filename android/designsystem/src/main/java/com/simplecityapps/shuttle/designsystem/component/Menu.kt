package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemShapes
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/**
 * One verb in a menu or an actions sheet. [destructive] draws it in `error` (Delete, Remove);
 * [selected] non-null makes it a choice (a sort field, the sort order), checked when true.
 */
data class S2Action(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val selected: Boolean? = null,
)

/**
 * An overflow or sort menu anchored to the composable that holds it: an Expressive grouped menu,
 * one `DropdownMenuGroup` per entry in [groups], with the group gap in place of dividers. Choosing
 * an item runs it and dismisses the menu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2Menu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<S2Action>>,
    modifier: Modifier = Modifier,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        S2MenuContent(groups = groups, onDismissRequest = onDismissRequest)
    }
}

/** The groups of an [S2Menu] without its popup, for laying a menu out in place. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2MenuContent(
    groups: List<List<S2Action>>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, groups.size)) {
                group.forEachIndexed { index, action ->
                    MenuItem(action, MenuDefaults.itemShape(index, group.size), onDismissRequest)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MenuItem(
    action: S2Action,
    shapes: MenuItemShapes,
    onDismissRequest: () -> Unit,
) {
    val onClick = {
        action.onClick()
        onDismissRequest()
    }
    val leadingIcon: (@Composable () -> Unit)? = action.icon?.let { icon -> { Icon(icon, contentDescription = null) } }
    if (action.selected != null) {
        SelectableDropdownMenuItem(
            selected = action.selected,
            onClick = onClick,
            text = { Text(action.label) },
            shapes = shapes,
            // An empty slot keeps unchecked choices aligned with the checked one.
            leadingIcon = leadingIcon ?: { Spacer(Modifier.size(24.dp)) },
            selectedLeadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
        )
    } else {
        DropdownMenuItem(
            onClick = onClick,
            text = { Text(action.label) },
            shape = shapes.shape,
            leadingIcon = leadingIcon,
            colors = if (action.destructive) {
                MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error)
            } else {
                MenuDefaults.itemColors()
            },
        )
    }
}

@Preview
@Composable
private fun S2MenuContentPreview() {
    S2Preview {
        S2MenuContent(
            groups = listOf(
                listOf(S2Action("Play", {}, Icons.Rounded.PlayArrow), S2Action("Add to playlist", {}, Icons.AutoMirrored.Rounded.PlaylistAdd)),
                listOf(S2Action("Delete", {}, Icons.Rounded.Delete, destructive = true)),
            ),
            onDismissRequest = {},
        )
    }
}
