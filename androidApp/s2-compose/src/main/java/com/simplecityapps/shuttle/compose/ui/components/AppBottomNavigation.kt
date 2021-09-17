package com.simplecityapps.shuttle.compose.ui.components;

import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.simplecityapps.shuttle.compose.ui.components.root.Screen
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme

@Composable
fun AppBottomNavigation(
    modifier: Modifier = Modifier,
    currentDestination: NavDestination?,
    onClick: (screen: Screen) -> Unit = {}
) {
    BottomNavigation(
        modifier = modifier,
        backgroundColor = MaterialColors.background
    ) {
        Screen.all.map { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            BottomNavigationItem(
                selected = selected,
                alwaysShowLabel = selected,
                icon = {
                    Icon(
                        imageVector = screen.image,
                        contentDescription = stringResource(id = screen.titleResId)
                    )
                },
                label = {
                    Text(
                        text = stringResource(id = screen.titleResId)
                    )
                },
                selectedContentColor = MaterialColors.onBackground,
                unselectedContentColor = MaterialColors.onBackground.copy(alpha = ContentAlpha.medium),
                onClick = { onClick(screen) }
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun AppBottomNavigationPreview() {
    Theme {
        AppBottomNavigation(currentDestination = null)
    }
}