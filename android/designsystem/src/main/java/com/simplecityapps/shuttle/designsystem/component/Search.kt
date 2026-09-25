package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import kotlinx.coroutines.launch

/**
 * Search, reachable from the Home and Library top bars: the collapsed M3 `SearchBar`, which
 * expands into the search view showing [content] (recent searches, then results as the user
 * types). The view is full screen on Compact and Medium and docked under the bar from Expanded
 * ([docked]), passed down by the caller from its width class.
 */
@Composable
fun S2SearchBar(
    state: SearchBarState,
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    docked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inputField = @Composable { SearchInputField(state, textFieldState, onSearch, placeholder) }
    SearchBar(state = state, inputField = inputField, modifier = modifier)
    if (docked) {
        ExpandedDockedSearchBar(state = state, inputField = inputField, content = content)
    } else {
        ExpandedFullScreenSearchBar(state = state, inputField = inputField, content = content)
    }
}

/**
 * The search text field, shared by the collapsed bar and the expanded view: a search icon that
 * becomes back once expanded, and a clear button while there is text.
 */
@Composable
fun SearchInputField(
    state: SearchBarState,
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val expanded = state.currentValue == SearchBarValue.Expanded
    SearchBarDefaults.InputField(
        textFieldState = textFieldState,
        searchBarState = state,
        onSearch = onSearch,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            if (expanded) {
                S2IconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.ds_back), { scope.launch { state.animateToCollapsed() } })
            } else {
                Icon(Icons.Rounded.Search, contentDescription = null)
            }
        },
        trailingIcon = if (textFieldState.text.isNotEmpty()) {
            { S2IconButton(Icons.Rounded.Close, stringResource(R.string.ds_clear_search), { textFieldState.clearText() }) }
        } else {
            null
        },
    )
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
private fun S2SearchBarPreview() {
    S2Preview {
        S2SearchBar(
            state = rememberSearchBarState(),
            textFieldState = rememberTextFieldState(),
            onSearch = {},
            placeholder = "Search your library",
        ) {}
    }
}
