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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SliderSetting
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsScreen
import com.squareup.phrase.Phrase
import java.text.DateFormat
import java.util.Locale
import kotlin.math.roundToInt

/** A settings screen: a collapsing large top bar over one list, as the shell's top-level screens use. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(
    title: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    content: LazyListScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The shell pads destinations clear of the nav bar and player; the bar takes the status bar.
        contentWindowInsets = WindowInsets(0),
        topBar = { S2LargeTopBar(title = title, onBack = onNavigateUp, actions = actions, scrollBehavior = scrollBehavior) },
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

/** The Settings root: the S2 Pro row, then one row per [SettingsDestination]. */
@Composable
fun SettingsRootScreen(
    onNavigateUp: () -> Unit,
    onOpenDestination: (SettingsDestination) -> Unit,
    onOpenPro: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsScaffold(title = stringResource(R.string.settings_menu_settings), onNavigateUp = onNavigateUp, modifier = modifier) {
        item {
            SettingsGroup(
                rows = listOf { shapes: ListItemShapes ->
                    LinkSetting(
                        title = stringResource(R.string.paywall_title),
                        summary = stringResource(R.string.paywall_settings_summary),
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
                            onClick = { onOpenDestination(destination) },
                            icon = destination.icon,
                            shapes = shapes
                        )
                    }
                }
            )
        }
    }
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
 * level are left out; a row whose `dependsOn` switch is off is disabled. About gets a version row when
 * [versionName] is set.
 */
@Composable
fun SettingsDestinationScreen(
    screen: SettingsScreen,
    uiState: SettingsUiState,
    onNavigateUp: () -> Unit,
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
            value = choiceValueLabel(item, uiState),
            onClick = { onOpenChoice(item) },
            enabled = enabled,
            shapes = shapes
        )

        is SettingItem.Slider<*> -> {
            val value = uiState.value(item.setting).toFloat()
            SliderSetting(
                title = stringResource(item.title),
                value = value,
                onValueChange = { onSliderChange(item, it) },
                valueRange = item.range,
                steps = item.steps,
                valueLabel = sliderValueLabel(item, value),
                enabled = enabled,
                shapes = shapes
            )
        }

        is SettingItem.Navigate -> LinkSetting(
            title = stringResource(item.title),
            onClick = { onOpenLink(item.target) },
            summary = summary,
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
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(lastScan)
    return "$label ${Phrase.from(resources, R.string.pref_last_scan_date).put("date", date).format()}"
}

private fun sliderValueLabel(
    item: SettingItem.Slider<*>,
    value: Float
): String? = when (item.setting) {
    PlaybackSettings.PreAmpGain -> String.format(Locale.getDefault(), "%+.1f dB", value)
    AppearanceSettings.WidgetBackgroundOpacity -> "${value.roundToInt()}%"
    else -> null
}
