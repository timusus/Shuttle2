package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import kotlinx.coroutines.launch

/**
 * A top-level destination in the nav bar or rail. [selectedIcon] is the filled variant shown while
 * the destination is selected. [badge] is null for no badge, empty for a dot, otherwise its text
 * ("3", "999+").
 */
data class S2NavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: String? = null,
)

/** The compact-width bottom navigation: a `ShortNavigationBar` of [items], [selectedIndex] selected. */
@Composable
fun S2NavigationBar(
    items: List<S2NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ShortNavigationBar(modifier = modifier) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            ShortNavigationBarItem(
                selected = selected,
                onClick = { onSelect(index) },
                icon = { NavIcon(item, selected) },
                label = { Text(item.label) },
            )
        }
    }
}

/**
 * The navigation for Medium width and up: a `WideNavigationRail` of [items]. The header button
 * toggles [state] between the collapsed rail (icons over labels) and the expanded one (icons beside
 * labels).
 */
@Composable
fun S2NavigationRail(
    items: List<S2NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: WideNavigationRailState = rememberWideNavigationRailState(),
) {
    val scope = rememberCoroutineScope()
    val expanded = state.targetValue == WideNavigationRailValue.Expanded
    WideNavigationRail(
        modifier = modifier,
        state = state,
        header = {
            S2IconButton(
                icon = if (expanded) Icons.AutoMirrored.Rounded.MenuOpen else Icons.Rounded.Menu,
                contentDescription = stringResource(if (expanded) R.string.ds_collapse_navigation else R.string.ds_expand_navigation),
                onClick = { scope.launch { state.toggle() } },
                modifier = Modifier.padding(start = 28.dp),
            )
        },
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            WideNavigationRailItem(
                selected = selected,
                onClick = { onSelect(index) },
                icon = { NavIcon(item, selected) },
                label = { Text(item.label) },
                railExpanded = expanded,
            )
        }
    }
}

@Composable
private fun NavIcon(item: S2NavItem, selected: Boolean) {
    val icon = if (selected) item.selectedIcon else item.icon
    val badge = item.badge
    if (badge == null) {
        Icon(icon, contentDescription = null)
    } else {
        BadgedBox(badge = { if (badge.isEmpty()) Badge() else Badge { Text(badge) } }) {
            Icon(icon, contentDescription = null)
        }
    }
}

@Preview
@Composable
private fun S2NavigationBarPreview() {
    S2Preview {
        S2NavigationBar(
            items = listOf(
                S2NavItem("Home", Icons.Outlined.Home, Icons.Rounded.Home),
                S2NavItem("Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic, badge = ""),
            ),
            selectedIndex = 0,
            onSelect = {},
        )
    }
}
