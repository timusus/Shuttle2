package com.simplecityapps.shuttle.compose.ui.components.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simplecityapps.shuttle.compose.ui.components.AppBottomNavigation
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.components.home.Home
import com.simplecityapps.shuttle.compose.ui.components.library.Library
import com.simplecityapps.shuttle.compose.ui.components.miniplayer.MiniPLayer
import com.simplecityapps.shuttle.compose.ui.components.offsetFraction
import com.simplecityapps.shuttle.compose.ui.components.search.Search
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Main(onShowSettings: () -> Unit = {}) {

    val homeNavController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        val bottomSheetScaffoldState = rememberBottomSheetScaffoldState()

        val bottomAppBarSize = 56.dp

        Scaffold(
            bottomBar = {
                BottomAppBar(
                    modifier = Modifier.offset(y = bottomAppBarSize * (1f - bottomSheetScaffoldState.offsetFraction())),
                    backgroundColor = MaterialColors.background
                ) {
                    val navBackStackEntry by homeNavController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    AppBottomNavigation(
                        modifier = Modifier,
                        currentDestination = currentDestination
                    ) { screen ->
                        if (screen is Screen.Main.Settings) {
                            // Show settings bottom sheet
                            onShowSettings()
                        } else {
                            homeNavController.navigate(screen.route)
                        }
                    }
                }
            }) {
            BottomSheetScaffold(
                modifier = Modifier.offset(y = -(bottomAppBarSize * (bottomSheetScaffoldState.offsetFraction()))),
                sheetBackgroundColor = MaterialColors.background,
                sheetContent = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialColors.background)
                    ) {
                        MiniPLayer()
                    }
                },
                scaffoldState = bottomSheetScaffoldState
            ) { padding ->
                Box(
                    Modifier
                        .padding(padding)
                        .offset(y = bottomAppBarSize * (bottomSheetScaffoldState.offsetFraction()))
                ) {
                    NavHost(
                        navController = homeNavController,
                        startDestination = Screen.Main.Library.route,
                        modifier = Modifier
                    ) {
                        composable(Screen.Main.Home.route) {
                            Home()
                        }
                        composable(Screen.Main.Library.route) {
                            Library()
                        }
                        composable(Screen.Main.Search.route) {
                            Search()
                        }
                    }
                }
            }
        }
    }

}

@Preview(showBackground = true)
@Composable
fun MainPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Main()
    }
}