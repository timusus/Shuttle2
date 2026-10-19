package com.simplecityapps.shuttle.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsScreen
import com.simplecityapps.shuttle.ui.text.SettingsString
import com.simplecityapps.shuttle.ui.text.UiText
import com.simplecityapps.shuttle.ui.text.stringResource
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The Settings root takes the collapsing large top bar; sub-pages take the standard bar so they read as a level down. */
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
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
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
                rows = settingsRootViewState(uiState).rows.map { row ->
                    { shapes: ListItemShapes ->
                        LinkSetting(
                            title = stringResource(row.destination.title),
                            summary = rootSummary(row.summary),
                            onClick = { onOpenDestination(row.destination) },
                            icon = row.destination.icon,
                            selected = selected?.let { row.destination == it },
                            shapes = shapes
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun rootSummary(summary: SettingsRootViewState.Summary): String? = when (summary) {
    SettingsRootViewState.Summary.None -> null
    is SettingsRootViewState.Summary.Option -> stringResource(summary.label)
    is SettingsRootViewState.Summary.Streaming -> stringResource(R.string.settings_sources_summary_streaming, stringResource(summary.quality))
    is SettingsRootViewState.Summary.Rescan -> stringResource(R.string.settings_library_summary_rescan, stringResource(summary.frequency))
    is SettingsRootViewState.Summary.EqualizerPreset -> stringResource(R.string.settings_playback_summary_equalizer_preset, stringResource(summary.preset))
    SettingsRootViewState.Summary.EqualizerOff -> stringResource(R.string.settings_playback_summary_equalizer_off)
    SettingsRootViewState.Summary.Privacy -> stringResource(R.string.settings_privacy_summary)
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

    val viewState = settingsDestinationViewState(screen, uiState, sdkInt, versionName)

    SettingsScaffold(
        title = stringResource(screen.destination.title),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        snackbarHostState = snackbarHostState
    ) {
        leadingContent()
        viewState.groups.forEach { group ->
            item(key = group.key) {
                SettingsGroup(
                    title = group.title?.let { stringResource(it) },
                    rows = group.rows.map { row ->
                        { shapes: ListItemShapes ->
                            SettingRow(
                                row = row,
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
        viewState.version?.let { version ->
            item(key = "version") {
                InfoSetting(title = stringResource(R.string.settings_version_title), summary = version)
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
    row: SettingsDestinationViewState.Row,
    shapes: ListItemShapes,
    onSwitchChange: (SettingItem.Switch, Boolean) -> Unit,
    onSliderChange: (SettingItem.Slider<*>, Float) -> Unit,
    onOpenChoice: (SettingItem.Choice<*>) -> Unit,
    onAction: (SettingItem.Action) -> Unit,
    onOpenLink: (SettingsLink) -> Unit
) {
    val summary = row.item.summary?.let { stringResource(it) }
    when (row) {
        is SettingsDestinationViewState.Row.Switch -> SwitchSetting(
            title = stringResource(row.item.title),
            checked = row.checked,
            onCheckedChange = { onSwitchChange(row.item, it) },
            summary = summary,
            enabled = row.enabled,
            shapes = shapes
        )

        is SettingsDestinationViewState.Row.Choice -> ChoiceSetting(
            title = stringResource(row.item.title),
            value = row.override?.let { stringResource(it) } ?: choiceValueLabel(row),
            summary = summary,
            onClick = { onOpenChoice(row.item) },
            enabled = row.enabled,
            shapes = shapes
        )

        is SettingsDestinationViewState.Row.Slider -> SliderSetting(
            title = stringResource(row.item.title),
            value = row.value,
            onValueChange = { onSliderChange(row.item, it) },
            valueRange = row.item.range,
            steps = row.item.steps,
            valueLabel = sliderValueLabel(row.valueLabel),
            enabled = row.enabled,
            modifier = Modifier.testTag(row.item.key),
            shapes = shapes
        )

        is SettingsDestinationViewState.Row.Navigate -> LinkSetting(
            title = stringResource(row.item.title),
            onClick = { onOpenLink(row.item.target) },
            summary = row.summary?.let { stringResource(it) },
            enabled = row.enabled,
            shapes = shapes
        )

        is SettingsDestinationViewState.Row.Action -> LinkSetting(
            title = stringResource(row.item.title),
            onClick = { onAction(row.item) },
            summary = summary,
            enabled = row.enabled,
            shapes = shapes
        )
    }
}

@Composable
private fun choiceValueLabel(row: SettingsDestinationViewState.Row.Choice): String {
    val label = row.label?.let { stringResource(it) }.orEmpty()
    val lastScan = row.lastScan ?: return label
    val resources = LocalContext.current.resources
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastScan.toEpochMilliseconds()))
    return "$label ${resources.getString(R.string.pref_last_scan_date, date)}"
}

@Composable
private fun sliderValueLabel(text: SettingsDestinationViewState.SliderText): String? = when (text) {
    SettingsDestinationViewState.SliderText.None -> null
    is SettingsDestinationViewState.SliderText.Decibels -> String.format(Locale.getDefault(), "%+.1f dB", text.value)
    SettingsDestinationViewState.SliderText.Off -> stringResource(SettingsString.DSP_REPLAY_GAIN_OFF)
    is SettingsDestinationViewState.SliderText.Seconds -> stringResource(UiText.Resource(SettingsString.SETTINGS_CROSSFADE_SECONDS, listOf(text.seconds)))
    is SettingsDestinationViewState.SliderText.Percent -> "${text.percent}%"
}
