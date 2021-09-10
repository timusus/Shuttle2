package com.simplecityapps.shuttle.compose.ui.main;

import androidx.annotation.StringRes
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable;
import androidx.compose.ui.graphics.vector.ImageVector;
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.S2androidTheme

@Composable
fun AppBottomNavigation(
    selected: Boolean,
    onClick: () -> Unit = {}
) {
    BottomNavigation(
        backgroundColor = MaterialColors.background
    ) {
        BottomNavItem.all.map {
            BottomNavigationItem(
                selected = false,
                alwaysShowLabel = selected,
                icon = {
                    Icon(
                        imageVector = it.image,
                        contentDescription = stringResource(id = it.titleResId)
                    )
                },
                label = {
                    Text(
                        text = stringResource(id = it.titleResId)
                    )
                },
                selectedContentColor = MaterialColors.onBackground,
                unselectedContentColor = MaterialColors.onBackground.copy(alpha = ContentAlpha.medium),
                onClick = onClick
            )
        }
    }
}

sealed class BottomNavItem(@StringRes val titleResId: Int, val image: ImageVector) {

    class Home : BottomNavItem(
        titleResId = R.string.title_home,
        image = Icons.Outlined.Home
    )

    class Library : BottomNavItem(
        titleResId = R.string.title_library,
        image = Icons.Outlined.QueueMusic
    )

    class Search : BottomNavItem(
        titleResId = R.string.title_search,
        image = Icons.Outlined.Search
    )

    class Settings : BottomNavItem(
        titleResId = R.string.title_settings,
        image = Icons.Outlined.Menu
    )

    companion object {
        val all = listOf(
            Home(),
            Library(),
            Search(),
            Settings()
        )
    }
}


@Preview(showBackground = true)
@Composable
fun AppBottomNavigationPreview() {
    S2androidTheme {
        AppBottomNavigation(true)
    }
}