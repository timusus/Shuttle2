package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.supporting

/*
 * Settings rows are `SegmentedListItem`s. A [SettingsGroup] stacks them with the segmented gap and
 * hands each row its `ListItemDefaults.segmentedShapes` for its place in the group, so the first
 * and last rows round their outer corners. A row outside a group takes the default shapes.
 */

/**
 * How a settings row draws its leading icon. [Tonal] sets it in a tonal rounded-square container and is
 * for top-level rows; [Plain] is the bare icon, for the rows under them, so the containers mark the
 * hierarchy instead of flattening it (#496).
 */
enum class SettingIconStyle { Tonal, Plain }

/** The title over a [SettingsGroup]. */
@Composable
fun SettingsHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = S2Spacing.medium, top = S2Spacing.medium, end = S2Spacing.medium, bottom = S2Spacing.small),
    )
}

/** A group of settings [rows] under an optional [title]; each row lambda receives its segmented shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsGroup(
    rows: List<@Composable (shapes: ListItemShapes) -> Unit>,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Column(modifier) {
        if (title != null) SettingsHeader(title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row -> row(ListItemDefaults.segmentedShapes(index, rows.size)) }
        }
    }
}

/** A setting that opens another screen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LinkSetting(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    iconStyle: SettingIconStyle = SettingIconStyle.Tonal,
    enabled: Boolean = true,
    progress: SettingProgress? = null,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        colors = settingColors(),
        enabled = enabled,
        leadingContent = icon?.let { { SettingIcon(it, iconStyle, enabled) } },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        supportingContent = settingSummary(summary, progress),
    ) { Text(title) }
}

/** An on/off setting; the whole row toggles [checked]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    progress: SettingProgress? = null,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        modifier = modifier,
        colors = settingColors(),
        enabled = enabled,
        leadingContent = icon?.let { { SettingIcon(it, SettingIconStyle.Tonal, enabled) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = settingSummary(summary, progress),
    ) { Text(title) }
}

/**
 * A group's actions, such as "Scan now": buttons that act in place, so unlike a [LinkSetting] the row has no
 * chevron and isn't itself clickable. [content] lays out `S2Button`s, the primary one first, under an optional
 * [summary] such as what an empty group means.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionsSetting(
    modifier: Modifier = Modifier,
    summary: String? = null,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    content: @Composable RowScope.() -> Unit,
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        colors = settingColors(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
            summary?.let { Text(it, style = MaterialTheme.typography.supporting, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(S2Spacing.small), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

/** A single-choice setting showing its current [value]; [onClick] opens the choice dialog. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChoiceSetting(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        colors = settingColors(),
        enabled = enabled,
        leadingContent = icon?.let { { SettingIcon(it, SettingIconStyle.Tonal, enabled) } },
        supportingContent = { Text(value) },
    ) { Text(title) }
}

/** A continuous setting: a `Slider` under the title, with the formatted [valueLabel] trailing. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SliderSetting(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        colors = settingColors(),
        enabled = enabled,
        leadingContent = icon?.let { { SettingIcon(it, SettingIconStyle.Tonal, enabled) } },
        trailingContent = valueLabel?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                )
            }
        },
        supportingContent = {
            Slider(value = value, onValueChange = onValueChange, enabled = enabled, valueRange = valueRange, steps = steps)
        },
    ) { Text(title) }
}

/** A read-only setting, such as the version number. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InfoSetting(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        colors = settingColors(),
        leadingContent = icon?.let { { SettingIcon(it, SettingIconStyle.Tonal, enabled = true) } },
        supportingContent = { Text(summary) },
    ) { Text(title) }
}

/** Work a settings row is doing, drawn as a wavy progress bar under its summary: [fraction] done, or null while it can't tell. */
@Immutable
data class SettingProgress(val fraction: Float? = null)

/** A row's supporting content: its [summary], with [progress]'s bar under it while there's work under way. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun settingSummary(summary: String?, progress: SettingProgress?): (@Composable () -> Unit)? {
    if (progress == null) return summary?.let { { Text(it) } }
    return {
        Column {
            summary?.let { Text(it) }
            val modifier = Modifier.fillMaxWidth().padding(top = S2Spacing.small).testTag("setting-progress")
            val fraction = progress.fraction
            if (fraction != null) LinearWavyProgressIndicator(progress = { fraction }, modifier = modifier) else LinearWavyProgressIndicator(modifier = modifier)
        }
    }
}

/**
 * Rows sit in `surfaceContainer` so a group reads as one container on the `surface` screen. A
 * checked switch row keeps the same colours: the `Switch` shows the state, not the row.
 */
@Composable
private fun settingColors(): ListItemColors {
    val colors = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = colors.surfaceContainer,
        disabledContainerColor = colors.surfaceContainer,
        selectedContainerColor = colors.surfaceContainer,
        selectedContentColor = colors.onSurface,
        selectedLeadingContentColor = colors.onSurfaceVariant,
        selectedTrailingContentColor = colors.onSurfaceVariant,
        selectedSupportingContentColor = colors.onSurfaceVariant,
    )
}

/**
 * The leading icon: for [SettingIconStyle.Tonal], in a [TonalIconContainer]; for
 * [SettingIconStyle.Plain], bare, in the row's own colour.
 */
@Composable
private fun SettingIcon(icon: ImageVector, style: SettingIconStyle, enabled: Boolean) {
    if (style == SettingIconStyle.Plain) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(S2IconSize.medium))
        return
    }
    TonalIconContainer(
        icon = icon,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        size = 40.dp,
        iconSize = S2IconSize.medium,
        modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
    )
}

@Preview
@Composable
private fun SettingsGroupPreview() {
    S2Preview {
        SettingsGroup(
            title = "Appearance",
            rows = listOf(
                { ChoiceSetting("Theme", "Follow system", {}, icon = Icons.Rounded.Palette, shapes = it) },
                { SwitchSetting("Dynamic colour", checked = true, onCheckedChange = {}, shapes = it) },
                { LinkSetting("Music", onClick = {}, summary = "/storage/emulated/0/Music", icon = Icons.Rounded.Folder, iconStyle = SettingIconStyle.Plain, shapes = it) },
                { LinkSetting("Jellyfin", onClick = {}, summary = "Syncing… 340 of 1,000 songs", progress = SettingProgress(0.34f), shapes = it) },
                { ActionsSetting(shapes = it) { S2Button("Scan now", onClick = {}, style = S2ButtonStyle.Tonal) } },
            ),
        )
    }
}
