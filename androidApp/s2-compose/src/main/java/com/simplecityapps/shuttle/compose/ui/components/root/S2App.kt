package com.simplecityapps.shuttle.compose.ui.components.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simplecityapps.shuttle.compose.ui.components.AppBottomNavigation
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.components.library.Library
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Root() {

    val navController = rememberNavController()

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
                    AppBottomNavigation(
                        modifier = Modifier,
                        selected = false
                    ) {
                    }
                }
            }) {
            BottomSheetScaffold(
                modifier = Modifier
                    .offset(y = -(bottomAppBarSize * (bottomSheetScaffoldState.offsetFraction()))),
                sheetContent = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialColors.primary)
                    )
                },
                scaffoldState = bottomSheetScaffoldState
            ) { padding ->
                Box(
                    Modifier
                        .padding(padding)
                        .offset(y = bottomAppBarSize * (bottomSheetScaffoldState.offsetFraction()))
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Library.route,
                        modifier = Modifier
                    ) {
                        composable(Screen.Library.route) {
                            Library()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
fun BottomSheetScaffoldState.offsetFraction(): Float {
    var offsetFraction = bottomSheetState.progress.fraction

    if (bottomSheetState.direction == 0f) {
        offsetFraction = if (bottomSheetState.isExpanded) 0f else 1f
    }

    if (bottomSheetState.direction < 0) {
        offsetFraction = 1f - offsetFraction
    }

    return offsetFraction
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Root()
    }
}