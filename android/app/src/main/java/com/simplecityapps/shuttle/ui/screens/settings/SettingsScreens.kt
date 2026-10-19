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
import com.simplecityapps.shuttle.ui.text.stringResource
import java.util.Locale
import java.util.TimeZone

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
    val viewState = settingsRootViewState(uiState, pro)
    val callbacks = SettingsCallbacks(onNavigateUp = onNavigateUp, onOpenPro = onOpenPro, onOpenDestination = onOpenDestination)
    val onEvent: (SettingsEvent) -> Unit = callbacks::onEvent
    SettingsScaffold(title = stringResource(R.string.settings_menu_settings), onNavigateUp = { onEvent(SettingsEvent.NavigateUp) }, modifier = modifier, root = true) {
        item {
            SettingsGroup(
                rows = listOf { shapes: ListItemShapes ->
                    LinkSetting(
                        title = stringResource(R.string.paywall_title),
                        summary = proSummaryText(viewState.pro, LocalContext.current.resources),
                        onClick = { onEvent(SettingsEvent.OpenPro) },
                        icon = Icons.Rounded.WorkspacePremium,
                        shapes = shapes
                    )
                }
            )
        }
        item {
            SettingsGroup(
                rows = viewState.rows.map { row ->
                    { shapes: ListItemShapes ->
                        LinkSetting(
                            title = stringResource(row.destination.title),
                            summary = rootSummaryText(row.summary, LocalContext.current.resources),
                            onClick = { onEvent(row.tapEvent()) },
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
    val callbacks = SettingsCallbacks(
        onNavigateUp = onNavigateUp ?: {},
        onSwitchChange = onSwitchChange,
        onChoiceSelect = onChoiceSelect,
        onSliderChange = onSliderChange,
        onAction = onAction,
        onOpenLink = onOpenLink
    )
    val onEvent: (SettingsEvent) -> Unit = callbacks::onEvent

    SettingsScaffold(
        title = stringResource(screen.destination.title),
        onNavigateUp = onNavigateUp?.let { { onEvent(SettingsEvent.NavigateUp) } },
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
                                onEvent = onEvent,
                                onOpenChoice = { openChoiceKey = onChoiceDialogEvent(openChoiceKey, ChoiceDialogEvent.Open(it.key)).openKey },
                                onAction = { item ->
                                    val step = onActionEvent(pendingAction, ActionEvent.Tap(item))
                                    pendingAction = step.pending
                                    step.event()?.let(onEvent)
                                }
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
        val dialog = choiceDialogViewState(choice, uiState)
        S2Dialog(
            title = stringResource(dialog.title),
            onDismissRequest = { openChoiceKey = onChoiceDialogEvent(openChoiceKey, ChoiceDialogEvent.Dismiss).openKey },
            dismissLabel = stringResource(android.R.string.cancel)
        ) {
            S2ChoiceList(
                options = dialog.options.map { stringResource(it) },
                selectedIndex = dialog.selectedIndex,
                onSelect = { index ->
                    val step = onChoiceDialogEvent(openChoiceKey, ChoiceDialogEvent.Select(index))
                    openChoiceKey = step.openKey
                    step.event(choice)?.let(onEvent)
                }
            )
        }
    }

    screen.items.filterIsInstance<SettingItem.Action>().firstOrNull { it.action == pendingAction }?.let(::confirmActionViewState)?.let { dialog ->
        S2Dialog(
            title = stringResource(dialog.title),
            onDismissRequest = { pendingAction = onActionEvent(pendingAction, ActionEvent.Dismiss).pending },
            confirmLabel = stringResource(dialog.confirm),
            onConfirm = {
                val step = onActionEvent(pendingAction, ActionEvent.Confirm)
                pendingAction = step.pending
                step.event()?.let(onEvent)
            },
            dismissLabel = stringResource(android.R.string.cancel),
            destructive = dialog.destructive
        ) {
            Text(stringResource(dialog.message))
        }
    }
}

@Composable
private fun SettingRow(
    row: SettingsDestinationViewState.Row,
    shapes: ListItemShapes,
    onEvent: (SettingsEvent) -> Unit,
    onOpenChoice: (SettingItem.Choice<*>) -> Unit,
    onAction: (SettingItem.Action) -> Unit
) {
    val summary = row.item.summary?.let { stringResource(it) }
    when (row) {
        is SettingsDestinationViewState.Row.Switch -> SwitchSetting(
            title = stringResource(row.item.title),
            checked = row.checked,
            onCheckedChange = { onEvent(SettingsEvent.ChangeSwitch(row.item, it)) },
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
            onValueChange = { onEvent(SettingsEvent.ChangeSlider(row.item, it)) },
            valueRange = row.item.range,
            steps = row.item.steps,
            valueLabel = sliderValueLabel(row.valueLabel),
            enabled = row.enabled,
            modifier = Modifier.testTag(row.item.key),
            shapes = shapes
        )

        is SettingsDestinationViewState.Row.Navigate -> LinkSetting(
            title = stringResource(row.item.title),
            onClick = { onEvent(row.tapEvent()) },
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
private fun choiceValueLabel(row: SettingsDestinationViewState.Row.Choice): String = choiceValueText(row, LocalContext.current.resources, Locale.getDefault(), TimeZone.getDefault())

@Composable
private fun sliderValueLabel(text: SettingsDestinationViewState.SliderText): String? = sliderValueText(text, LocalContext.current.resources, Locale.getDefault())
