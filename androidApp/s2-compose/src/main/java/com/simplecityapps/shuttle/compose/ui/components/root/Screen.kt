package com.simplecityapps.shuttle.compose.ui.components.root

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.simplecityapps.shuttle.compose.R

sealed class Screen(
    val route: String,
) {

    sealed class Root(
        route: String
    ) : Screen(route) {

        object Main : Screen.Root(
            route = "main"
        )

        object Settings : Screen.Root(
            route = "settings"
        )

        object Dsp : Screen.Root(
            route = "dsp"
        )
    }

    sealed class Main(
        route: String,
        @StringRes val titleResId: Int,
        val image: ImageVector
    ) : Screen(route) {

        object Home : Screen.Main(
            route = "home",
            titleResId = R.string.title_home,
            image = Icons.Outlined.Home
        )

        object Library : Screen.Main(
            route = "library",
            titleResId = R.string.title_library,
            image = Icons.Outlined.QueueMusic
        )

        object Search : Screen.Main(
            route = "search",
            titleResId = R.string.title_search,
            image = Icons.Outlined.Search
        )

        object Settings : Screen.Main(
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
}