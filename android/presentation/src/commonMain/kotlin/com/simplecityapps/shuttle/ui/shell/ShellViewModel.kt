package com.simplecityapps.shuttle.ui.shell

import androidx.lifecycle.ViewModel
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ReadSetting
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
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
@ViewModelKey(ShellViewModel::class)
@ContributesIntoMap(AppScope::class)
class ShellViewModel @Inject constructor(
    readSetting: ReadSetting,
) : ViewModel() {
    val uiState: StateFlow<ShellUiState> = MutableStateFlow(
        ShellUiState(startTab = if (readSetting(AppearanceSettings.ShowHomeOnLaunch)) ShellTab.Home else ShellTab.Library),
    ).asStateFlow()
}
