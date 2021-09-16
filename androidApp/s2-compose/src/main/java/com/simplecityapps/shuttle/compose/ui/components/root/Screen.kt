package com.simplecityapps.shuttle.compose.ui.components.root

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.simplecityapps.shuttle.compose.R

sealed class Screen(val route: String, @StringRes val titleResId: Int, val image: ImageVector) {

    object Home : Screen(
        route = "home",
        titleResId = R.string.title_home,
        image = Icons.Outlined.Home
    )

    object Library : Screen(
        route = "library",
        titleResId = R.string.title_library,
        image = Icons.Outlined.QueueMusic
    )

    object Search : Screen(
        route = "search",
        titleResId = R.string.title_search,
        image = Icons.Outlined.Search
    )

    object Settings : Screen(
        route = "settings",
        titleResId = R.string.title_settings,
        image = Icons.Outlined.Menu
    )

    companion object {
        val all = listOf(
            Home,
            Library,
            Search,
            Settings
        )
    }
}