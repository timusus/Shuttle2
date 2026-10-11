package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2IconScrim
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing

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

/**
 * The one-row bar, pinned unless the screen passes an `enterAlwaysScrollBehavior` (a landscape phone's Library and Search): on sub-screens (Settings' sub-pages among them), and over the Library container's section chips, where only the chips stay pinned under it and each tab's
 * controls row scrolls away with its page.
 */
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

/**
 * The pinned bar over an artwork detail screen's [DetailHero] (album, artist, genre, playlist). Until [collapsed] it is
 * transparent and untitled, so the hero's wash runs up behind its icons; once the hero's title has scrolled under it,
 * it takes the regular bar colour and fades in [title] and [subtitle]. [overImage]: it sits over a [DetailBleedHero],
 * so its icons are white until then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S2DetailTopBar(
    title: String,
    subtitle: String?,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    overImage: Boolean = false,
) {
    val defaults = TopAppBarDefaults.topAppBarColors()
    val containerColor by animateColorAsState(if (collapsed) defaults.containerColor else Color.Transparent, label = "detailTopBarContainer")
    // Over a full-bleed image (and its scrim) the icons are white until the bar fills in.
    val onImage = overImage && !collapsed
    val navigationColor by animateColorAsState(if (onImage) Color.White else defaults.navigationIconContentColor, label = "detailTopBarNavigation")
    val actionColor by animateColorAsState(if (onImage) Color.White else defaults.actionIconContentColor, label = "detailTopBarActions")
    // Circular containers behind the icons while the bar is transparent, so they hold 3:1 whatever the art or wash
    // under them: black over an image (white icons on it), the surface colour over the wash.
    val scrimColor by animateColorAsState(
        when {
            collapsed -> Color.Transparent
            overImage -> S2IconScrim.overImage
            else -> MaterialTheme.colorScheme.surface.copy(alpha = S2IconScrim.overWashAlpha)
        },
        label = "detailTopBarScrim",
    )
    TopAppBar(
        title = {
            AnimatedVisibility(visible = collapsed, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.testTag("detail-top-bar-title")) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        modifier = modifier,
        // Padded to the 48 dp touch target around the 40 dp button, so the scrim doesn't hug the icon.
        navigationIcon = {
            onBack?.let { BackButton(it, Modifier.background(scrimColor, S2IconScrim.shape).padding(S2Spacing.xsmall)) }
        },
        actions = { Row(Modifier.background(scrimColor, S2IconScrim.shape).padding(S2Spacing.xsmall), content = actions) },
        // The pinned scroll behaviour would otherwise swap in its own colour as soon as the list moves.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            navigationIconContentColor = navigationColor,
            actionIconContentColor = actionColor,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
internal fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    S2IconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.ds_back), onClick, modifier)
}

@Preview
@Composable
private fun S2LargeTopBarPreview() {
    S2Preview {
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
