package com.simplecityapps.shuttle.ui.shell

import androidx.lifecycle.ViewModel
import com.simplecityapps.shuttle.settings.AppearanceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShellUiState(
    /**
     * The tab the app opens on: Home when Show Home on launch is on, else Library. Read once, so a change in
     * Settings applies from the next launch, and back at the root of another tab keeps returning to this one.
     */
    val startTab: ShellTab,
)

/** The shell's launch state, from Settings. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    appearanceSettings: AppearanceSettings,
) : ViewModel() {
    val uiState: StateFlow<ShellUiState> = MutableStateFlow(
        ShellUiState(startTab = if (appearanceSettings.showHomeOnLaunch.value) ShellTab.Home else ShellTab.Library),
    ).asStateFlow()
}
