package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/**
 * The sort control above a list: an `AssistChip` naming the sort [field], with an arrow for the
 * order and a drop-down arrow. [onClick] opens the sort [S2Menu], anchored to the chip's parent.
 */
@Composable
fun S2SortChip(
    field: String,
    ascending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(field) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = {
            Icon(
                if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                contentDescription = stringResource(if (ascending) R.string.ds_sort_ascending else R.string.ds_sort_descending),
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

/** A toggleable list filter ("Downloaded", "Favourites"): a `FilterChip` that shows a check while [selected]. */
@Composable
fun S2FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/** An applied filter that the user removes rather than toggles (a source such as "Jellyfin"): an `InputChip` with a close icon. */
@Composable
fun S2InputChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        trailingIcon = {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ds_remove_filter), modifier = Modifier.size(InputChipDefaults.IconSize))
        },
    )
}

/**
 * A read-only fact about an item ("FLAC", "96 kHz"): a `SuggestionChip` that can't be tapped. M3
 * chips are all clickable, so it is the disabled chip drawn in the enabled chip's colours, and it
 * reads as its [label] alone rather than as a disabled button.
 */
@Composable
fun S2InfoChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    SuggestionChip(
        onClick = {},
        label = { Text(label) },
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(label) },
        enabled = false,
        colors = SuggestionChipDefaults.suggestionChipColors(disabledLabelColor = colors.onSurfaceVariant),
        border = SuggestionChipDefaults.suggestionChipBorder(enabled = false, disabledBorderColor = colors.outlineVariant),
    )
}

@Preview
@Composable
private fun ChipsPreview() {
    S2Preview {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            S2SortChip("Title", ascending = true, onClick = {})
            S2FilterChip("Downloaded", selected = true, onClick = {})
            S2InputChip("Jellyfin", onRemove = {})
            S2InfoChip("FLAC")
        }
    }
}
