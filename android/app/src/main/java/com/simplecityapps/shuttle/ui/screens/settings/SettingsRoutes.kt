package com.simplecityapps.shuttle.ui.screens.settings

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.entitlement.PaywallSource
import com.simplecityapps.shuttle.model.MediaProviderType
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
import com.simplecityapps.shuttle.ui.screens.settings.scrobbling.ScrobblingScreen
import com.simplecityapps.shuttle.ui.screens.settings.scrobbling.ScrobblingViewModel
import com.simplecityapps.shuttle.ui.screens.sources.ServerDetailEntry
import com.simplecityapps.shuttle.ui.screens.sources.ThisDeviceEntry
import com.simplecityapps.shuttle.ui.screens.sources.sourcesRows
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.shell.LocalListBesideDetail
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
data object ScrobblingRoute : UtilityRoute

@Serializable
data object WhatsNewRoute : UtilityRoute

@Serializable
data object LicencesRoute : UtilityRoute

@Serializable
data object LiveLogRoute : UtilityRoute

/** Settings > Sources > This device: its switch and status, scanning and the folder rules. */
@Serializable
data object ThisDeviceRoute : UtilityRoute

/** Settings > Sources > a server, by its [MediaProviderType] name. */
@Serializable
data class ServerDetailRoute(val typeName: String) : UtilityRoute

/**
 * The Settings screens' entries, for the shell's entry provider. Settings is the list pane and its pages the detail
 * pane (list-detail from Expanded, one pane and push navigation below it), in a scene of their own; beside the list
 * [SettingsPlaceholderPage] stands in until a page is opened, and picking a page in the list replaces the open one.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.settingsEntries(navigator: AppNavigator) {
    val navigateUp = { navigator.back() }
    val openLink = { link: SettingsLink -> navigator.open(link.route) }
    val openThisDevice = { navigator.open(ThisDeviceRoute) }
    val openServer = { type: MediaProviderType -> navigator.open(ServerDetailRoute(type.name)) }
    // The list entry's ViewModel store, lent to the stand-in page: the scene composes it outside any entry, where
    // metroViewModel() would reach the activity's store and keep a SettingsViewModel for the activity's lifetime.
    val listStore = mutableStateOf<ViewModelStoreOwner?>(null)
    entry<SettingsRoute>(
        metadata = settingsListPane {
            listStore.value?.let { owner ->
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    SettingsDestinationEntry(SettingsPlaceholderPage, onNavigateUp = null, onOpenLink = openLink, onOpenThisDevice = openThisDevice, onOpenServer = openServer)
                }
            }
        }
    ) {
        val owner = checkNotNull(LocalViewModelStoreOwner.current)
        DisposableEffect(owner) {
            listStore.value = owner
            onDispose { if (listStore.value === owner) listStore.value = null }
        }
        val viewModel: SettingsViewModel = metroViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val proViewModel: SettingsProViewModel = metroViewModel()
        val pro by proViewModel.proState.collectAsStateWithLifecycle()
        SettingsList(navigator, uiState, pro)
    }
    entry<SettingsDestinationRoute>(metadata = SettingsDetailPane) { route ->
        SettingsDestinationEntry(route.destination, onNavigateUp = { navigateUp() }, onOpenLink = openLink, onOpenThisDevice = openThisDevice, onOpenServer = openServer)
    }
    entry<ThisDeviceRoute>(metadata = SettingsDetailPane) { ThisDeviceEntry(onNavigateUp = { navigateUp() }) }
    entry<ServerDetailRoute>(metadata = SettingsDetailPane) { route -> ServerDetailEntry(route.typeName, onNavigateUp = { navigateUp() }) }
    entry<EqualizerRoute>(metadata = SettingsDetailPane) { EqualizerEntry(onNavigateUp = { navigateUp() }) }
    entry<ExcludedSongsRoute>(metadata = SettingsDetailPane) { ExcludedSongsEntry(onNavigateUp = { navigateUp() }) }
    entry<ScrobblingRoute>(metadata = SettingsDetailPane) { ScrobblingEntry(onNavigateUp = { navigateUp() }) }
    entry<LiveLogRoute>(metadata = SettingsDetailPane) { LiveLogEntry(onNavigateUp = { navigateUp() }) }
    entry<WhatsNewRoute>(metadata = SettingsDetailPane) {
        val viewModel: WhatsNewViewModel = metroViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        WhatsNewScreen(uiState = uiState, onNavigateUp = { navigateUp() })
    }
    entry<LicencesRoute>(metadata = SettingsDetailPane) {
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

/** Settings' own list-detail scene, so a Settings opened over Library's list and detail never joins their scene. */
private const val SettingsSceneKey = "settings"

/** [SettingsRoute]'s pane: the list, with [placeholder] beside it until a page is opened. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun settingsListPane(placeholder: @Composable () -> Unit): Map<String, Any> = ListDetailSceneStrategy.listPane(sceneKey = SettingsSceneKey, detailPlaceholder = { placeholder() })

/** The pane of every page under Settings: the detail beside its list. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal val SettingsDetailPane: Map<String, Any> = ListDetailSceneStrategy.detailPane(sceneKey = SettingsSceneKey)

/**
 * The Settings list: picking a page replaces whatever is open above the list, so beside it one page shows at a time
 * and back closes it; in one pane the list is on top and the page is pushed. Beside a page, its row is marked.
 */
@Composable
internal fun SettingsList(
    navigator: AppNavigator,
    uiState: SettingsUiState,
    pro: SettingsProState
) {
    SettingsRootScreen(
        uiState = uiState,
        pro = pro,
        onNavigateUp = { navigator.back() },
        onOpenDestination = { navigator.replaceAbove(SettingsRoute, SettingsDestinationRoute(it)) },
        onOpenPro = { navigator.open(PaywallRoute(PaywallSource.Settings)) },
        selected = if (LocalListBesideDetail.current) settingsPageBesideList(navigator.stack(navigator.selectedTab)) else null
    )
}

@Composable
private fun SettingsDestinationEntry(
    destination: SettingsDestination,
    onNavigateUp: (() -> Unit)?,
    onOpenLink: (SettingsLink) -> Unit,
    onOpenThisDevice: () -> Unit,
    onOpenServer: (MediaProviderType) -> Unit
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
        screen = AndroidSettingsCatalog.screen(destination).let { if (uiState.lastFmConfigured) it else it.withoutScrobbling() },
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onSwitchChange = viewModel::onSwitchChange,
        onChoiceSelect = viewModel::onChoiceSelect,
        onSliderChange = viewModel::onSliderChange,
        onAction = viewModel::onAction,
        onOpenLink = onOpenLink,
        versionName = BuildConfig.VERSION_NAME,
        snackbarHostState = snackbarHostState,
        leadingContent = if (destination == SettingsDestination.Sources) sourcesRows(onOpenThisDevice, onOpenServer) else ({})
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

        is SettingsUiEvent.DebugLogsShared -> when (result) {
            ShareDebugLogsResult.Shared -> null
            ShareDebugLogsResult.Empty -> R.string.settings_logging_logs_empty
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
private fun ScrobblingEntry(onNavigateUp: () -> Unit) {
    val viewModel: ScrobblingViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScrobblingScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onSignIn = viewModel::onSignIn,
        onFinishSignIn = viewModel::onFinishSignIn,
        onSignOut = viewModel::onSignOut,
        onServerStreamsChange = viewModel::onServerStreamsChange,
        onApprovalUrlOpened = viewModel::onApprovalUrlOpened,
        onMessageShown = viewModel::onMessageShown
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

/**
 * The restore snackbar text: counts of songs matched and playlists added or updated, or a note that nothing matched,
 * then whether the backup's settings were restored.
 */
internal fun backupImportedMessage(
    resources: Resources,
    event: SettingsUiEvent.BackupImported
): String {
    val settingsRestored = event.settingsRestored > 0
    if (event.songsMatched == 0 && event.playlistsRestored == 0) {
        return resources.getString(if (settingsRestored) R.string.settings_backup_import_settings_only else R.string.settings_backup_import_nothing)
    }
    val library = libraryRestoredMessage(resources, event)
    return if (settingsRestored) resources.getString(R.string.settings_backup_import_with_settings, library) else library
}

private fun libraryRestoredMessage(
    resources: Resources,
    event: SettingsUiEvent.BackupImported
): String {
    val songs = resources.getQuantityString(R.plurals.settings_backup_import_songs, event.songsMatched, event.songsMatched)
    val playlists = resources.getQuantityString(R.plurals.settings_backup_import_playlists, event.playlistsRestored, event.playlistsRestored)
    return if (event.songsUnmatched > 0) {
        val unmatched = resources.getQuantityString(R.plurals.settings_backup_import_unmatched, event.songsUnmatched, event.songsUnmatched)
        resources.getString(R.string.settings_backup_import_done_unmatched, songs, playlists, unmatched)
    } else {
        resources.getString(R.string.settings_backup_import_done, songs, playlists)
    }
}
