package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.heroTitle
import com.simplecityapps.shuttle.designsystem.theme.rowSubtitle

/**
 * The Search destination's field (reached from the Home and Library top bars): the M3 search bar's input field on its
 * container, with a search icon and a clear button while there is text. [focusRequester] lets the screen focus it on
 * arrival.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S2SearchField(
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
        modifier = modifier,
    ) {
        SearchBarDefaults.InputField(
            state = textFieldState,
            onSearch = onSearch,
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (textFieldState.text.isNotEmpty()) {
                { S2IconButton(Icons.Rounded.Close, stringResource(R.string.ds_clear_search), { textFieldState.clearText() }) }
            } else {
                null
            },
        )
    }
}

/** A recent search in the search view: tap to search it again, or remove it. */
@Composable
fun SearchRecentRow(query: String, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
        trailingContent = { S2IconButton(Icons.Rounded.Close, stringResource(R.string.ds_remove_recent_search), onRemove) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(query)
    }
}

/**
 * The best match of a search, lifted over the rows as a card: its [artwork] at feature size, what [kind] of thing it is,
 * its [title] and [subtitle], and a Play button. Tapping the card opens it.
 */
@Composable
fun SearchTopResult(
    kind: String,
    title: AnnotatedString,
    subtitle: AnnotatedString?,
    playLabel: String,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = S2Spacing.medium),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(S2Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(S2Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            artwork()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(S2Spacing.tiny)) {
                Text(kind, style = MaterialTheme.typography.rowSubtitle, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.heroTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.rowSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                S2Button(
                    text = playLabel,
                    onClick = onPlay,
                    style = S2ButtonStyle.Tonal,
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.padding(top = S2Spacing.small),
                )
            }
        }
    }
}

/** What the search view shows when [query] matches nothing. */
@Composable
fun SearchNoResults(query: String, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.ds_search_no_results, query),
        message = stringResource(R.string.ds_search_no_results_message),
        icon = Icons.Rounded.SearchOff,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun S2SearchFieldPreview() {
    S2Preview {
        S2SearchField(
            textFieldState = rememberTextFieldState(),
            onSearch = {},
            placeholder = "Search your library",
        )
    }
}
