package com.simplecityapps.shuttle.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ChoiceSetting
import com.simplecityapps.shuttle.designsystem.component.InfoSetting
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.S2ChoiceList
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.S2LargeTopBar
import com.simplecityapps.shuttle.designsystem.component.S2SnackbarHost
import com.simplecityapps.shuttle.designsystem.component.S2TopBar
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SliderSetting
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingOverride
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsScreen
import com.simplecityapps.shuttle.ui.text.StringKey
import com.simplecityapps.shuttle.ui.text.UiText
import com.simplecityapps.shuttle.ui.text.stringResource
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A settings screen: a top bar over one list. The Settings [root] takes the collapsing large top bar the shell's
 * top-level screens use; its sub-pages take the standard one-row bar, so they read as a level down (#496).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(
    title: String,
    onNavigateUp: (() -> Unit)?,
    modifier: Modifier = Modifier,
    root: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(horizontal = S2Spacing.medium, vertical = S2Spacing.small),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(S2Spacing.medium),
    content: LazyListScope.() -> Unit
) {
    val scrollBehavior = if (root) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The shell pads destinations clear of the nav bar and player; the bar takes the status bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (root) {
                S2LargeTopBar(title = title, onBack = onNavigateUp, actions = actions, scrollBehavior = scrollBehavior)
            } else {
                S2TopBar(title = title, onBack = onNavigateUp, actions = actions, scrollBehavior = scrollBehavior)
            }
        },
        snackbarHost = { snackbarHostState?.let { S2SnackbarHost(it) } }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

/**
 * The Settings root: the Shuttle Music Pro row, then one row per [SettingsDestination], each showing the current
 * value of what it holds where there is one. [uiState] carries the stored settings; [pro] says which copy the Pro
 * row shows. [selected] is the page open beside the list, at widths that show both; null in one pane, where the rows
 * carry no selection state.
 */
@Composable
fun SettingsRootScreen(
    uiState: SettingsUiState,
    pro: SettingsProState,
    onNavigateUp: () -> Unit,
    onOpenDestination: (SettingsDestination) -> Unit,
    onOpenPro: () -> Unit,
    modifier: Modifier = Modifier,
    selected: SettingsDestination? = null
) {
    SettingsScaffold(title = stringResource(R.string.settings_menu_settings), onNavigateUp = onNavigateUp, modifier = modifier, root = true) {
        item {
            SettingsGroup(
                rows = listOf { shapes: ListItemShapes ->
                    LinkSetting(
                        title = stringResource(R.string.paywall_title),
                        summary = when (pro) {
                            SettingsProState.Neutral -> null
                            SettingsProState.Owned -> stringResource(R.string.paywall_settings_summary_pro)
                            SettingsProState.Upsell -> stringResource(R.string.paywall_settings_summary)
                        },
                        onClick = onOpenPro,
                        icon = Icons.Rounded.WorkspacePremium,
                        shapes = shapes
                    )
                }
            )
        }
        item {
            SettingsGroup(
                rows = SettingsDestination.entries.map { destination ->
                    { shapes: ListItemShapes ->
                        LinkSetting(
                            title = stringResource(destination.title),
                            summary = destinationSummary(destination, uiState),
                            onClick = { onOpenDestination(destination) },
                            icon = destination.icon,
                            selected = selected?.let { destination == it },
                            shapes = shapes
                        )
                    }
                }
            )
        }
    }
}

/** The current value a root row shows under its [destination]'s title, or null where nothing one-line sums it up. */
@Composable
private fun destinationSummary(
    destination: SettingsDestination,
    uiState: SettingsUiState
): String? = when (destination) {
    SettingsDestination.Appearance -> choiceLabel(destination, uiState, AppearanceSettings.Theme)

    SettingsDestination.Sources -> choiceLabel(destination, uiState, StreamingSettings.MeteredQuality)
        ?.let { stringResource(R.string.settings_sources_summary_streaming, it) }

    SettingsDestination.Library -> choiceLabel(destination, uiState, LibrarySettings.RescanFrequency)
        ?.let { stringResource(R.string.settings_library_summary_rescan, it) }

    SettingsDestination.PlaybackAndSound -> if (uiState.value(EqualizerSettings.Enabled)) {
        stringResource(R.string.settings_playback_summary_equalizer_preset, stringResource(uiState.equalizerSummary))
    } else {
        stringResource(R.string.settings_playback_summary_equalizer_off)
    }

    else -> null
}

/** The label of the option [setting] is on, from the choice row [destination] shows for it. */
@Composable
private fun <T> choiceLabel(
    destination: SettingsDestination,
    uiState: SettingsUiState,
    setting: Setting<T>
): String? {
    val value = uiState.value(setting)
    return AndroidSettingsCatalog.screen(destination).items
        .filterIsInstance<SettingItem.Choice<*>>()
        .firstOrNull { it.setting.key == setting.key }
        ?.options?.firstOrNull { it.value == value }
        ?.let { stringResource(it.label) }
}

private val SettingsDestination.icon: ImageVector
    get() = when (this) {
        SettingsDestination.Appearance -> Icons.Rounded.Palette
        SettingsDestination.PlaybackAndSound -> Icons.Rounded.GraphicEq
        SettingsDestination.Sources -> Icons.Rounded.Storage
        SettingsDestination.Library -> Icons.Rounded.LibraryMusic
        SettingsDestination.Privacy -> Icons.Rounded.PrivacyTip
        SettingsDestination.About -> Icons.Rounded.Info
    }

/**
 * One settings destination, rendered from its catalog [screen] after any [leadingContent]. Rows below [sdkInt]'s
 * level are left out; a row whose `dependsOn` switch is off is disabled, as is a choice whose `overriddenBy`
 * switch is shown and on. About gets a version row when [versionName] is set. With no [onNavigateUp] the bar has no Up
 * button, as for the page standing in beside the Settings list.
 */
@Composable
fun SettingsDestinationScreen(
    screen: SettingsScreen,
    uiState: SettingsUiState,
    onNavigateUp: (() -> Unit)?,
    onSwitchChange: (SettingItem.Switch, Boolean) -> Unit,
    onChoiceSelect: (SettingItem.Choice<*>, Int) -> Unit,
    onSliderChange: (SettingItem.Slider<*>, Float) -> Unit,
    onAction: (SettingsAction) -> Unit,
    onOpenLink: (SettingsLink) -> Unit,
    modifier: Modifier = Modifier,
    sdkInt: Int = Build.VERSION.SDK_INT,
    versionName: String? = null,
    /** Rows a destination adds ahead of its catalog groups, such as Sources' folders and servers. */
    leadingContent: LazyListScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var openChoiceKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAction by rememberSaveable { mutableStateOf<SettingsAction?>(null) }

    val groups = screen.groups
        .map { group -> group to group.items.filter { it.minSdk <= sdkInt } }
        .filter { (_, items) -> items.isNotEmpty() }
    val shownKeys = groups.flatMap { (_, items) -> items.mapNotNull { it.key } }.toSet()

    SettingsScaffold(
        title = stringResource(screen.destination.title),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        snackbarHostState = snackbarHostState
    ) {
        leadingContent()
        groups.forEach { (group, items) ->
            item(key = group.title ?: items.first().title) {
                SettingsGroup(
                    title = group.title?.let { stringResource(it) },
                    rows = items.map { item ->
                        { shapes: ListItemShapes ->
                            SettingRow(
                                item = item,
                                uiState = uiState,
                                override = (item as? SettingItem.Choice<*>)?.overriddenBy?.takeIf { it.setting.key in shownKeys && uiState.value(it.setting) },
                                shapes = shapes,
                                onSwitchChange = onSwitchChange,
                                onSliderChange = onSliderChange,
                                onOpenChoice = { openChoiceKey = it.key },
                                onAction = { action ->
                                    if (action.confirmation != null) pendingAction = action.action else onAction(action.action)
                                },
                                onOpenLink = onOpenLink
                            )
                        }
                    }
                )
            }
        }
        if (versionName != null && screen.destination == SettingsDestination.About) {
            item(key = "version") {
                InfoSetting(title = stringResource(R.string.settings_version_title), summary = versionName)
            }
        }
    }

    screen.items.filterIsInstance<SettingItem.Choice<*>>().firstOrNull { it.key == openChoiceKey }?.let { choice ->
        S2Dialog(
            title = stringResource(choice.title),
            onDismissRequest = { openChoiceKey = null },
            dismissLabel = stringResource(android.R.string.cancel)
        ) {
            S2ChoiceList(
                options = choice.options.map { stringResource(it.label) },
                selectedIndex = choice.options.indexOfFirst { it.value == uiState.value(choice.setting) },
                onSelect = { index ->
                    openChoiceKey = null
                    onChoiceSelect(choice, index)
                }
            )
        }
    }

    screen.items.filterIsInstance<SettingItem.Action>().firstOrNull { it.action == pendingAction }?.confirmation?.let { confirmation ->
        val action = pendingAction!!
        S2Dialog(
            title = stringResource(confirmation.title),
            onDismissRequest = { pendingAction = null },
            confirmLabel = stringResource(confirmation.confirm),
            onConfirm = {
                pendingAction = null
                onAction(action)
            },
            dismissLabel = stringResource(android.R.string.cancel),
            destructive = action == SettingsAction.ClearArtworkCache
        ) {
            Text(stringResource(confirmation.message))
        }
    }
}

@Composable
private fun SettingRow(
    item: SettingItem,
    uiState: SettingsUiState,
    override: SettingOverride?,
    shapes: ListItemShapes,
    onSwitchChange: (SettingItem.Switch, Boolean) -> Unit,
    onSliderChange: (SettingItem.Slider<*>, Float) -> Unit,
    onOpenChoice: (SettingItem.Choice<*>) -> Unit,
    onAction: (SettingItem.Action) -> Unit,
    onOpenLink: (SettingsLink) -> Unit
) {
    val enabled = item.dependsOn?.let { uiState.value(it) } ?: true
    val summary = item.summary?.let { stringResource(it) }
    when (item) {
        is SettingItem.Switch -> SwitchSetting(
            title = stringResource(item.title),
            checked = uiState.value(item.setting),
            onCheckedChange = { onSwitchChange(item, it) },
            summary = summary,
            enabled = enabled,
            shapes = shapes
        )

        is SettingItem.Choice<*> -> ChoiceSetting(
            title = stringResource(item.title),
            value = override?.let { stringResource(it.hint) } ?: choiceValueLabel(item, uiState),
            summary = summary,
            onClick = { onOpenChoice(item) },
            enabled = enabled && override == null,
            shapes = shapes
        )

        is SettingItem.Slider<*> -> {
            // A restored preference can sit outside the range; the row shows the clamped position.
            val value = uiState.value(item.setting).toFloat().coerceIn(item.range.start, item.range.endInclusive)
            SliderSetting(
                title = stringResource(item.title),
                value = value,
                onValueChange = { onSliderChange(item, it) },
                valueRange = item.range,
                steps = item.steps,
                valueLabel = sliderValueLabel(item, value),
                enabled = enabled,
                modifier = Modifier.testTag(item.key),
                shapes = shapes
            )
        }

        is SettingItem.Navigate -> LinkSetting(
            title = stringResource(item.title),
            onClick = { onOpenLink(item.target) },
            summary = if (item.target == SettingsLink.Equalizer) stringResource(uiState.equalizerSummary) else summary,
            enabled = enabled,
            shapes = shapes
        )

        is SettingItem.Action -> LinkSetting(
            title = stringResource(item.title),
            onClick = { onAction(item) },
            summary = summary,
            enabled = enabled,
            shapes = shapes
        )
    }
}

@Composable
private fun choiceValueLabel(
    item: SettingItem.Choice<*>,
    uiState: SettingsUiState
): String {
    val current = uiState.value(item.setting)
    val label = item.options.firstOrNull { it.value == current }?.let { stringResource(it.label) }.orEmpty()
    val lastScan = uiState.lastScanDate
    if (item.setting != LibrarySettings.RescanFrequency || lastScan == null) return label
    val resources = LocalContext.current.resources
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastScan.toEpochMilliseconds()))
    return "$label ${resources.getString(R.string.pref_last_scan_date, date)}"
}

@Composable
private fun sliderValueLabel(
    item: SettingItem.Slider<*>,
    value: Float
): String? = when (item.setting) {
    PlaybackSettings.PreAmpGain -> String.format(Locale.getDefault(), "%+.1f dB", value)

    PlaybackSettings.CrossfadeDuration -> if (value < 500f) {
        stringResource(StringKey.DSP_REPLAY_GAIN_OFF)
    } else {
        stringResource(UiText.Resource(StringKey.SETTINGS_CROSSFADE_SECONDS, listOf((value / 1000f).roundToInt())))
    }

    AppearanceSettings.WidgetBackgroundOpacity -> "${value.roundToInt()}%"

    else -> null
}
