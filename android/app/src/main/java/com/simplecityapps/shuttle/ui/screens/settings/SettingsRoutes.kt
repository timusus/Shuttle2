package com.simplecityapps.shuttle.ui.screens.settings

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.entitlement.PaywallSource
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
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.screens.sources.FolderRulesEntry
import com.simplecityapps.shuttle.ui.screens.sources.sourcesRows
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.shell.SettingsRoute
import com.simplecityapps.shuttle.ui.shell.UtilityRoute
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.serialization.Serializable
import timber.log.Timber

// Routes under Settings. SettingsRoute itself lives with the shell's routes, since the shell opens it.

@Serializable
data class SettingsDestinationRoute(
    val destination: SettingsDestination
) : UtilityRoute

@Serializable
data object EqualizerRoute : UtilityRoute

@Serializable
data object ExcludedSongsRoute : UtilityRoute

@Serializable
data object WhatsNewRoute : UtilityRoute

@Serializable
data object LicencesRoute : UtilityRoute

@Serializable
data object LiveLogRoute : UtilityRoute

@Serializable
data object FolderRulesRoute : UtilityRoute

/**
 * The Settings screens' entries, for the shell's entry provider. Settings is the list pane and its pages the detail
 * pane (list-detail from Expanded, one pane and push navigation below it); beside the list the first page stands in
 * until one is opened, and opening another replaces the open one rather than stacking.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
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
    val detail = ListDetailSceneStrategy.detailPane()
    // True while the list pane has the default page beside it, so the list lights that page's row.
    val defaultPageShown = mutableStateOf(false)
    val openDestination = { destination: SettingsDestination ->
        val route = SettingsDestinationRoute(destination)
        if (navigator.stack(navigator.selectedTab).last() is SettingsDestinationRoute) navigator.replace(route) else navigator.open(route)
    }
    entry<SettingsRoute>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = {
            DisposableEffect(Unit) {
                defaultPageShown.value = true
                onDispose { defaultPageShown.value = false }
            }
            SettingsDestinationEntry(SettingsDestination.entries.first(), onNavigateUp = {}, onOpenLink = openLink, onOpenFolderRules = { navigator.open(FolderRulesRoute) })
        })
    ) {
        val viewModel: SettingsViewModel = metroViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val proViewModel: SettingsProViewModel = metroViewModel()
        val pro by proViewModel.proState.collectAsStateWithLifecycle()
        SettingsRootScreen(
            uiState = uiState,
            pro = pro,
            onNavigateUp = { navigateUp() },
            onOpenDestination = openDestination,
            onOpenPro = { navigator.open(PaywallRoute(PaywallSource.Settings)) },
            selected = (navigator.stack(navigator.selectedTab).last() as? SettingsDestinationRoute)?.destination
                ?: SettingsDestination.entries.first().takeIf { defaultPageShown.value }
        )
    }
    entry<SettingsDestinationRoute>(metadata = detail) { route ->
        SettingsDestinationEntry(route.destination, onNavigateUp = { navigateUp() }, onOpenLink = openLink, onOpenFolderRules = { navigator.open(FolderRulesRoute) })
    }
    entry<FolderRulesRoute>(metadata = detail) { FolderRulesEntry(onNavigateUp = { navigateUp() }) }
    entry<EqualizerRoute>(metadata = detail) { EqualizerEntry(onNavigateUp = { navigateUp() }) }
    entry<ExcludedSongsRoute>(metadata = detail) { ExcludedSongsEntry(onNavigateUp = { navigateUp() }) }
    entry<LiveLogRoute>(metadata = detail) { LiveLogEntry(onNavigateUp = { navigateUp() }) }
    entry<WhatsNewRoute>(metadata = detail) {
        val viewModel: WhatsNewViewModel = metroViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        WhatsNewScreen(uiState = uiState, onNavigateUp = { navigateUp() })
    }
    entry<LicencesRoute>(metadata = detail) {
        val viewModel: LicencesViewModel = metroViewModel()
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
    onOpenLink: (SettingsLink) -> Unit,
    onOpenFolderRules: () -> Unit
) {
    val viewModel: SettingsViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportBackupTo(it.toString()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackupFrom(it.toString()) }
    }
    ConsumeEvents(uiState.events, viewModel::onEventHandled) { event ->
        when (event) {
            is SettingsUiEvent.BackupExportRequested -> exportLauncher.launch(event.suggestedName)
            is SettingsUiEvent.BackupImportPickerRequested -> importLauncher.launch(arrayOf("application/json"))
            is SettingsUiEvent.BackupImported -> snackbarHostState.showSnackbar(backupImportedMessage(context.resources, event))
            else -> snackbarHostState.showSnackbar(context.getString(checkNotNull(event.message)))
        }
    }
    SettingsDestinationScreen(
        screen = AndroidSettingsCatalog.screen(destination),
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onSwitchChange = viewModel::onSwitchChange,
        onChoiceSelect = viewModel::onChoiceSelect,
        onSliderChange = viewModel::onSliderChange,
        onAction = viewModel::onAction,
        onOpenLink = onOpenLink,
        versionName = BuildConfig.VERSION_NAME,
        snackbarHostState = snackbarHostState,
        leadingContent = if (destination == SettingsDestination.Sources) sourcesRows(onOpenFolderRules) else ({})
    )
}

@get:StringRes
private val SettingsUiEvent.message: Int?
    get() = when (this) {
        SettingsUiEvent.RescanStarted -> R.string.settings_rescan_started

        SettingsUiEvent.BackupExportSaved -> R.string.settings_backup_export_saved

        SettingsUiEvent.BackupExportFailed -> R.string.settings_backup_export_failed

        SettingsUiEvent.BackupImportFailed -> R.string.settings_backup_import_failed

        // Handled with launchers and report formatting where the events are consumed, never as plain snackbars.
        is SettingsUiEvent.BackupExportRequested,
        is SettingsUiEvent.BackupImportPickerRequested,
        is SettingsUiEvent.BackupImported -> null

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
    val viewModel: EqualizerViewModel = metroViewModel()
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
    val viewModel: ExcludedSongsViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExcludedSongsScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onInclude = viewModel::onInclude,
        onIncludeAll = viewModel::onIncludeAll
    )
}

@Composable
private fun LiveLogEntry(onNavigateUp: () -> Unit) {
    val gate: LiveLogGateViewModel = metroViewModel()
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

/** The restore snackbar text: counts of songs matched and playlists added or updated, or a note that nothing matched. */
internal fun backupImportedMessage(
    resources: Resources,
    event: SettingsUiEvent.BackupImported
): String {
    if (event.songsMatched == 0 && event.playlistsRestored == 0) {
        return resources.getString(R.string.settings_backup_import_nothing)
    }
    val songs = resources.getQuantityString(R.plurals.settings_backup_import_songs, event.songsMatched, event.songsMatched)
    val playlists = resources.getQuantityString(R.plurals.settings_backup_import_playlists, event.playlistsRestored, event.playlistsRestored)
    return if (event.songsUnmatched > 0) {
        val unmatched = resources.getQuantityString(R.plurals.settings_backup_import_unmatched, event.songsUnmatched, event.songsUnmatched)
        resources.getString(R.string.settings_backup_import_done_unmatched, songs, playlists, unmatched)
    } else {
        resources.getString(R.string.settings_backup_import_done, songs, playlists)
    }
}
