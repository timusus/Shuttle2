package com.simplecityapps.shuttle.ui.screens.welcome

import androidx.lifecycle.ViewModel
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccessCoordinator
import com.simplecityapps.shuttle.ui.screens.sources.isLocal
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WelcomeUiState(val showWelcome: Boolean)

/** Shown before the shell on a new install; seeing it counts as the one-time analytics notice. */
@ViewModelKey(WelcomeViewModel::class)
@ContributesIntoMap(AppScope::class)
class WelcomeViewModel @Inject constructor(
    private val readSetting: ReadSetting,
    private val saveSetting: SaveSetting,
    private val mediaSources: MediaSources,
    private val musicAccess: MusicAccessCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WelcomeUiState(showWelcome = readSetting(WelcomeSettings.Pending) && !hasServer()))
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        if (_uiState.value.showWelcome) saveSetting(AnalyticsConsentSettings.NoticeShown, true)
    }

    /** "Music on this device" got the system prompt's answer: scan if granted, and either way the Library takes over. */
    fun onMusicAccessResult(granted: Boolean, showRationale: Boolean) {
        musicAccess.onResult(granted, showRationale)
        finish()
    }

    /** The server picker or its sign-in closed: done if a server connected, otherwise back to the choice. */
    fun onServerFlowClosed() {
        if (hasServer()) finish()
    }

    /** Leaves for Settings > Privacy, with the empty Library's actions to come back to. */
    fun onOpenPrivacy() = finish()

    private fun hasServer() = mediaSources.enabledTypes.value.any { !it.isLocal }

    private fun finish() {
        saveSetting(WelcomeSettings.Pending, false)
        _uiState.value = WelcomeUiState(showWelcome = false)
    }
}
