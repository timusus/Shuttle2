package com.simplecityapps.shuttle.compose.ui.components.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.simplecityapps.shuttle.compose.ui.BottomSettings
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.components.settings.bottomsheet.SettingsBottomSheet
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Root() {

    val systemUiController = rememberSystemUiController()
    val useDarkIcons = MaterialTheme.colors.isLight
    val backgroundColor = MaterialColors.background

    SideEffect {
        systemUiController.setSystemBarsColor(
            color = backgroundColor,
            darkIcons = useDarkIcons
        )
    }

    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val settingsBottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    ModalBottomSheetLayout(
        sheetContent = {
            SettingsBottomSheet(onItemSelected = { bottomSettings ->
                scope.launch {
                    settingsBottomSheetState.hide()
                }
                when (bottomSettings) {
                    BottomSettings.Shuffle -> {
                        // Todo:
                    }
                    BottomSettings.SleepTimer -> {
                        // Todo:
                    }
                    BottomSettings.Dsp -> {
                        navController.navigate(Screen.Root.Dsp.route)
                    }
                    BottomSettings.Settings -> {
                        navController.navigate(Screen.Root.Settings.route)
                    }
                }
            })
        },
        sheetState = settingsBottomSheetState
    ) {
        NavHost(navController = navController, Screen.Root.Main.route) {
            composable(Screen.Root.Main.route) {
                Main(
                    onShowSettings = {
                        scope.launch {
                            settingsBottomSheetState.show()
                        }
                    }
                )
            }
            composable(Screen.Root.Dsp.route) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Blue.copy(alpha = 0.2f))
                )
            }
            composable(Screen.Root.Settings.route) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Green.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RootPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Root()
    }
}