package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/**
 * The actions for a multi-selection: a clear button, the [selectedCount], an icon button per
 * [actions] entry (each needs an icon) and, when [overflowActions] has groups, a More button that
 * opens them in an [S2Menu].
 *
 * Floating by default, a vibrant `HorizontalFloatingToolbar` over the list; [docked] lays the same
 * content in a `FlexibleBottomAppBar` instead. Both are here until the owner picks one.
 */
@Composable
fun S2SelectionToolbar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    actions: List<S2Action>,
    modifier: Modifier = Modifier,
    overflowActions: List<List<S2Action>> = emptyList(),
    docked: Boolean = false,
) {
    if (docked) {
        FlexibleBottomAppBar(modifier = modifier) {
            SelectionContent(selectedCount, onClearSelection, actions, overflowActions, fill = true)
        }
    } else {
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = modifier,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        ) {
            SelectionContent(selectedCount, onClearSelection, actions, overflowActions, fill = false)
        }
    }
}

@Composable
private fun RowScope.SelectionContent(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    actions: List<S2Action>,
    overflowActions: List<List<S2Action>>,
    fill: Boolean,
) {
    S2IconButton(Icons.Rounded.Close, stringResource(R.string.ds_clear_selection), onClearSelection)
    Text(
        pluralStringResource(R.plurals.ds_selected_count, selectedCount, selectedCount),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(start = 4.dp)
            .align(Alignment.CenterVertically)
            .then(if (fill) Modifier.weight(1f) else Modifier),
    )
    if (!fill) Spacer(Modifier.width(12.dp))
    actions.forEach { action ->
        S2IconButton(requireNotNull(action.icon) { "Selection actions need an icon" }, action.label, action.onClick)
    }
    if (overflowActions.isNotEmpty()) {
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            S2IconButton(Icons.Rounded.MoreVert, stringResource(R.string.ds_more_options), { menuOpen = true })
            S2Menu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, groups = overflowActions)
        }
    }
}

@Preview
@Composable
private fun S2SelectionToolbarPreview() {
    S2Theme {
        S2SelectionToolbar(
            selectedCount = 3,
            onClearSelection = {},
            actions = listOf(S2Action("Play", {}, Icons.Rounded.PlayArrow), S2Action("Add to playlist", {}, Icons.AutoMirrored.Rounded.PlaylistAdd)),
        )
    }
}
