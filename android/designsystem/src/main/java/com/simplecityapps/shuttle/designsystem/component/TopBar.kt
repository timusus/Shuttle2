package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/**
 * The bar on top-level and list screens (Home, Library, Playlists, Settings): the Expressive
 * `LargeFlexibleTopAppBar` with a [title] and an optional [subtitle] (the library count). Connect
 * [scrollBehavior] (`exitUntilCollapsedScrollBehavior`) to the screen's list and it collapses to
 * one row as the list scrolls. A collapsing bar, not a hero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S2LargeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        navigationIcon = { onBack?.let { BackButton(it) } },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/** The pinned one-row bar on artwork detail screens (album, artist, genre, playlist) and sub-screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S2TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = { onBack?.let { BackButton(it) } },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
internal fun BackButton(onClick: () -> Unit) {
    S2IconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.ds_back), onClick)
}

@Preview
@Composable
private fun S2LargeTopBarPreview() {
    S2Theme {
        S2LargeTopBar(
            title = "Library",
            subtitle = "3,310 songs",
            actions = {
                S2IconButton(Icons.Rounded.Search, stringResource(R.string.ds_search), {})
                S2IconButton(Icons.Rounded.MoreVert, stringResource(R.string.ds_more_options), {})
            },
        )
    }
}
