package com.simplecityapps.shuttle.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.screens.paywall.PaywallRoute
import com.simplecityapps.shuttle.ui.screens.settings.about.LicencesScreen
import com.simplecityapps.shuttle.ui.screens.settings.about.LicencesViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.WhatsNewScreen
import com.simplecityapps.shuttle.ui.screens.settings.about.WhatsNewViewModel
import com.simplecityapps.shuttle.ui.screens.settings.equalizer.EqualizerScreen
import com.simplecityapps.shuttle.ui.screens.settings.equalizer.EqualizerViewModel
import com.simplecityapps.shuttle.ui.screens.settings.excluded.ExcludedSongsScreen
import com.simplecityapps.shuttle.ui.screens.settings.excluded.ExcludedSongsViewModel
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.screens.sources.sourcesRows
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.shell.SettingsRoute
import com.simplecityapps.trial.PaywallSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import timber.log.Timber

// Routes under Settings. SettingsRoute itself lives with the shell's routes, since the shell opens it.

@Serializable
data class SettingsDestinationRoute(
    val destination: SettingsDestination
) : NavKey

@Serializable
data object EqualizerRoute : NavKey

@Serializable
data object ExcludedSongsRoute : NavKey

@Serializable
data object WhatsNewRoute : NavKey

@Serializable
data object LicencesRoute : NavKey

@Serializable
data object LiveLogRoute : NavKey

/** The Settings screens' entries, for the shell's entry provider. */
fun EntryProviderScope<NavKey>.settingsEntries(navigator: AppNavigator) {
    val navigateUp = { navigator.back() }
    val openLink = { link: SettingsLink ->
        when (link) {
            SettingsLink.Equalizer -> navigator.open(EqualizerRoute)
            SettingsLink.ExcludedSongs -> navigator.open(ExcludedSongsRoute)
            SettingsLink.WhatsNew -> navigator.open(WhatsNewRoute)
            SettingsLink.Licences -> navigator.open(LicencesRoute)
            SettingsLink.LiveLog -> navigator.open(LiveLogRoute)
        }
    }
    entry<SettingsRoute> {
        SettingsRootScreen(
            onNavigateUp = { navigateUp() },
            onOpenDestination = { navigator.open(SettingsDestinationRoute(it)) },
            onOpenPro = { navigator.open(PaywallRoute(PaywallSource.Settings)) }
        )
    }
    entry<SettingsDestinationRoute> { route -> SettingsDestinationEntry(route.destination, onNavigateUp = { navigateUp() }, onOpenLink = openLink) }
    entry<EqualizerRoute> { EqualizerEntry(onNavigateUp = { navigateUp() }) }
    entry<ExcludedSongsRoute> { ExcludedSongsEntry(onNavigateUp = { navigateUp() }) }
    entry<LiveLogRoute> { LiveLogEntry(onNavigateUp = { navigateUp() }) }
    entry<WhatsNewRoute> {
        val viewModel: WhatsNewViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        WhatsNewScreen(uiState = uiState, onNavigateUp = { navigateUp() })
    }
    entry<LicencesRoute> {
        val viewModel: LicencesViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val uriHandler = LocalUriHandler.current
        LicencesScreen(
            uiState = uiState,
            onNavigateUp = { navigateUp() },
            onOpenWebsite = { url -> runCatching { uriHandler.openUri(url) }.onFailure { Timber.w(it, "No app to open $url") } }
        )
    }
}

@Composable
private fun SettingsDestinationEntry(
    destination: SettingsDestination,
    onNavigateUp: () -> Unit,
    onOpenLink: (SettingsLink) -> Unit
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    ConsumeEvents(uiState.events, viewModel::onEventHandled) { event ->
        snackbarHostState.showSnackbar(context.getString(event.message))
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    SettingsDestinationScreen(
        screen = SettingsCatalog.screen(destination),
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onSwitchChange = viewModel::onSwitchChange,
        onChoiceSelect = viewModel::onChoiceSelect,
        onSliderChange = viewModel::onSliderChange,
        onAction = viewModel::onAction,
        onOpenLink = onOpenLink,
        versionName = BuildConfig.VERSION_NAME,
        snackbarHostState = snackbarHostState,
        leadingContent = if (destination == SettingsDestination.Sources) sourcesRows(snackbarHostState) else ({})
    )
}

@get:StringRes
private val SettingsUiEvent.message: Int
    get() = when (this) {
        SettingsUiEvent.RescanStarted -> R.string.settings_rescan_started

        SettingsUiEvent.ArtworkCacheCleared -> R.string.settings_artwork_cache_cleared

        SettingsUiEvent.ArtworkDownloadStarted -> R.string.settings_artwork_download_started

        is SettingsUiEvent.DebugLogsCopied -> when (result) {
            CopyDebugLogsResult.Copied -> R.string.settings_logging_clipboard_logs_copied
            CopyDebugLogsResult.TooLarge -> R.string.settings_logging_clipboard_logs_too_large
            CopyDebugLogsResult.Empty -> R.string.settings_logging_clipboard_logs_empty
        }
    }

@Composable
private fun EqualizerEntry(onNavigateUp: () -> Unit) {
    val viewModel: EqualizerViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    EqualizerScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEnabledChange = viewModel::onEnabledChange,
        onPresetSelect = viewModel::onPresetSelect,
        onBandGainChange = viewModel::onBandGainChange,
        onBandGainChangeFinished = viewModel::onBandGainChangeFinished,
        onPreampGainChange = viewModel::onPreampGainChange
    )
}

@Composable
private fun ExcludedSongsEntry(onNavigateUp: () -> Unit) {
    val viewModel: ExcludedSongsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExcludedSongsScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onInclude = viewModel::onInclude,
        onIncludeAll = viewModel::onIncludeAll
    )
}

data class LiveLogGateUiState(val entryPoint: LiveLogEntryPoint? = null)

/**
 * Resolves the debug-only [LiveLogEntryPoint] via Hilt's optional binding, so this file never imports a
 * class that only exists in the debug build. Absent in release, where the row that opens this route is also
 * hidden (see [com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog]).
 */
@HiltViewModel
class LiveLogGateViewModel @Inject constructor(
    entryPoint: Optional<LiveLogEntryPoint>
) : ViewModel() {
    val uiState: StateFlow<LiveLogGateUiState> = MutableStateFlow(LiveLogGateUiState(entryPoint.orElse(null))).asStateFlow()
}

@Composable
private fun LiveLogEntry(onNavigateUp: () -> Unit) {
    val gate: LiveLogGateViewModel = hiltViewModel()
    val uiState by gate.uiState.collectAsStateWithLifecycle()
    val entryPoint = uiState.entryPoint
    if (entryPoint != null) {
        entryPoint.Content(onNavigateUp = onNavigateUp)
    } else {
        LifecycleResumeEffect(Unit) {
            onNavigateUp()
            onPauseOrDispose {}
        }
    }
}
