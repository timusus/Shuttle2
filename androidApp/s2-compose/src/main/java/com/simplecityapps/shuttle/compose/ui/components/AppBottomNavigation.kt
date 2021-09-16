package com.simplecityapps.shuttle.compose.ui.components;

import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.compose.ui.components.root.Screen
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme

@Composable
fun AppBottomNavigation(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit = {}
) {
    BottomNavigation(
        modifier = modifier,
        backgroundColor = MaterialColors.background
    ) {
        Screen.all.map {
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


@Preview(showBackground = true)
@Composable
fun AppBottomNavigationPreview() {
    Theme {
        AppBottomNavigation(selected = true)
    }
}