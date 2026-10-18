package com.simplecityapps.shuttle.ui.shell

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2NavItem
import com.simplecityapps.shuttle.designsystem.component.S2NavigationBar
import com.simplecityapps.shuttle.designsystem.component.S2NavigationRail

@get:StringRes
private val ShellTab.label: Int
    get() = when (this) {
        ShellTab.Home -> R.string.shell_tab_home
        ShellTab.Library -> R.string.title_library
        ShellTab.Search -> R.string.shell_tab_search
    }

@Composable
private fun ShellTab.navItem(): S2NavItem {
    val label = stringResource(label)
    return when (this) {
        ShellTab.Home -> S2NavItem(label, Icons.Outlined.Home, Icons.Rounded.Home)
        ShellTab.Library -> S2NavItem(label, Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic)
        ShellTab.Search -> S2NavItem(label, Icons.Outlined.Search, Icons.Rounded.Search)
    }
}

@Composable
private fun tabItems(): List<S2NavItem> = ShellTab.entries.map { it.navItem() }

/** 1 with the nav bar shown, 0 with it hidden, moving between them with the player pane's motion. */
@Composable
internal fun rememberNavigationShown(shown: Boolean): State<Float> = animateFloatAsState(if (shown) 1f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "navigation")

/** The nav bar, lighting no tab without a [selectedTab]. */
@Composable
internal fun ShellNavigationBar(
    selectedTab: ShellTab?,
    onSelectTab: (ShellTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    S2NavigationBar(
        items = tabItems(),
        selectedIndex = selectedTab?.ordinal ?: -1,
        onSelect = { index -> onSelectTab(ShellTab.entries[index]) },
        modifier = modifier.testTag(ShellTestTags.NavigationBar),
    )
}

/** Tags tests find the shell's own chrome by. */
internal object ShellTestTags {
    const val NavigationBar = "shell-navigation-bar"
    const val NavigationRail = "shell-navigation-rail"

    /** The box the destinations lay out in, inside the padding that keeps them clear of the chrome and the insets. */
    const val Destinations = "shell-destinations"
}

/** The rail, which slides away without a [selectedTab] the way the player pane does. */
@Composable
internal fun AnimatedShellRail(
    selectedTab: ShellTab?,
    expanded: Boolean,
    showToggle: Boolean,
    onSelectTab: (ShellTab) -> Unit,
) {
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<IntSize>()
    AnimatedVisibility(
        visible = selectedTab != null,
        modifier = Modifier.testTag(ShellTestTags.NavigationRail),
        enter = expandHorizontally(spec),
        exit = shrinkHorizontally(spec),
    ) {
        ShellRail(selectedTab, expanded, showToggle, onSelectTab)
    }
}

@Composable
private fun ShellRail(
    selectedTab: ShellTab?,
    expanded: Boolean,
    showToggle: Boolean,
    onSelectTab: (ShellTab) -> Unit,
) {
    val state = rememberWideNavigationRailState(if (expanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed)
    LaunchedEffect(state, expanded) { if (expanded) state.expand() else state.collapse() }
    S2NavigationRail(
        items = tabItems(),
        selectedIndex = selectedTab?.ordinal ?: -1,
        onSelect = { index -> onSelectTab(ShellTab.entries[index]) },
        state = state,
        showToggle = showToggle,
    )
}
