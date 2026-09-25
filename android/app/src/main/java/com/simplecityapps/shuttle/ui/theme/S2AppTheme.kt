package com.simplecityapps.shuttle.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.designsystem.theme.S2Accent
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppThemeState(
    val theme: ThemeMode = ThemeMode.DayNight,
    val accent: Accent = Accent.Default,
    val dynamicColour: Boolean = false,
    val pureBlack: Boolean = false
)

/** The Appearance settings the Compose theme follows, live, so a change in Settings restyles the app without a restart. */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    appearanceSettings: AppearanceSettings
) : ViewModel() {
    val state: StateFlow<AppThemeState> = combine(
        appearanceSettings.theme.flow,
        appearanceSettings.accent.flow,
        appearanceSettings.dynamicColour.flow,
        appearanceSettings.pureBlack.flow,
        ::AppThemeState
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppThemeState(
            theme = appearanceSettings.theme.value,
            accent = appearanceSettings.accent.value,
            dynamicColour = appearanceSettings.dynamicColour.value,
            pureBlack = appearanceSettings.pureBlack.value
        )
    )
}

/** [S2Theme] styled by the user's Appearance settings. */
@Composable
fun S2AppTheme(
    viewModel: AppThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    S2AppTheme(state, content)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2AppTheme(
    state: AppThemeState,
    content: @Composable () -> Unit
) {
    val darkTheme = when (state.theme) {
        ThemeMode.DayNight -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    S2Theme(darkTheme = darkTheme, accent = state.accent.toS2Accent(), dynamicColor = state.dynamicColour) {
        if (darkTheme && state.pureBlack) {
            val colorScheme = MaterialTheme.colorScheme
            MaterialTheme(
                colorScheme = colorScheme.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black),
                motionScheme = MaterialTheme.motionScheme,
                shapes = MaterialTheme.shapes,
                typography = MaterialTheme.typography,
                content = content
            )
        } else {
            content()
        }
    }
}

private fun Accent.toS2Accent(): S2Accent = when (this) {
    Accent.Default -> S2Accent.Default
    Accent.Orange -> S2Accent.Orange
    Accent.Cyan -> S2Accent.Cyan
    Accent.Purple -> S2Accent.Purple
    Accent.Green -> S2Accent.Green
    Accent.Amber -> S2Accent.Amber
}
