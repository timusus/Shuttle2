package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.LocalCompactMode

/*
 * Settings rows are `SegmentedListItem`s. A [SettingsGroup] stacks them with the segmented gap and
 * hands each row its `ListItemDefaults.segmentedShapes` for its place in the group, so the first
 * and last rows round their outer corners. A row outside a group takes the default shapes.
 */

/**
 * How a settings row draws its leading icon. [Plain] is the bare icon and the default everywhere,
 * keeping rows slim; [Tonal] sets it in a tonal cookie-shaped container for the odd row that needs
 * extra weight.
 */
enum class SettingIconStyle { Tonal, Plain }

/** The title over a [SettingsGroup]. */
@Composable
fun SettingsHeader(title: String, modifier: Modifier = Modifier) {
    val compact = LocalCompactMode.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = if (compact) 8.dp else 16.dp, end = 16.dp, bottom = if (compact) 2.dp else 8.dp),
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
    iconStyle: SettingIconStyle = SettingIconStyle.Plain,
    enabled: Boolean = true,
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
        supportingContent = summary?.let { { Text(it) } },
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
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        modifier = modifier,
        colors = settingColors(),
        enabled = enabled,
        leadingContent = icon?.let { { SettingIcon(it, SettingIconStyle.Plain, enabled) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = summary?.let { { Text(it) } },
    ) { Text(title) }
}

/**
 * An on/off setting with trailing move-up/move-down actions, for an ordered list the user enables
 * and reorders in place (the Library's tabs). Tapping the row toggles [checked]; the switch itself
 * stays directly operable, and [switchModifier] carries e.g. a test tag for it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReorderSwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    moveUpEnabled: Boolean,
    moveDownEnabled: Boolean,
    moveUpContentDescription: String,
    moveDownContentDescription: String,
    modifier: Modifier = Modifier,
    switchModifier: Modifier = Modifier,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = shapes,
        modifier = modifier,
        colors = settingColors(),
        leadingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = switchModifier) },
        trailingContent = {
            Row {
                S2IconButton(icon = Icons.Rounded.ArrowUpward, contentDescription = moveUpContentDescription, onClick = onMoveUp, enabled = moveUpEnabled)
                S2IconButton(icon = Icons.Rounded.ArrowDownward, contentDescription = moveDownContentDescription, onClick = onMoveDown, enabled = moveDownEnabled)
            }
        },
    ) { Text(title) }
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
        leadingContent = icon?.let { { SettingIcon(it, SettingIconStyle.Plain, enabled) } },
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
        leadingContent = icon?.let { { SettingIcon(it, SettingIconStyle.Plain, enabled) } },
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
        leadingContent = icon?.let { { SettingIcon(it, SettingIconStyle.Plain, enabled = true) } },
        supportingContent = { Text(summary) },
    ) { Text(title) }
}

/**
 * Rows are transparent on the `surface` screen, so a group reads as plain rows rather than a
 * tonal container. A checked switch row keeps the same colours: the `Switch` shows the state,
 * not the row.
 */
@Composable
private fun settingColors(): ListItemColors {
    val colors = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        selectedContentColor = colors.onSurface,
        selectedLeadingContentColor = colors.onSurfaceVariant,
        selectedTrailingContentColor = colors.onSurfaceVariant,
        selectedSupportingContentColor = colors.onSurfaceVariant,
    )
}

/**
 * The leading icon: for [SettingIconStyle.Tonal], in a `MaterialShapes.Cookie4Sided` container, as
 * design-language.md allows for settings; for [SettingIconStyle.Plain], bare, in the row's own colour.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingIcon(icon: ImageVector, style: SettingIconStyle, enabled: Boolean) {
    if (style == SettingIconStyle.Plain) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(MaterialShapes.Cookie4Sided.toShape())
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
    }
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
            ),
        )
    }
}

@Preview
@Composable
private fun ReorderSwitchSettingPreview() {
    S2Preview {
        SettingsGroup(
            rows = listOf(
                {
                    ReorderSwitchSetting(
                        title = "Songs",
                        checked = true,
                        onCheckedChange = {},
                        onMoveUp = {},
                        onMoveDown = {},
                        moveUpEnabled = false,
                        moveDownEnabled = true,
                        moveUpContentDescription = "Move up",
                        moveDownContentDescription = "Move down",
                        shapes = it,
                    )
                },
                {
                    ReorderSwitchSetting(
                        title = "Folders",
                        checked = false,
                        onCheckedChange = {},
                        onMoveUp = {},
                        onMoveDown = {},
                        moveUpEnabled = true,
                        moveDownEnabled = false,
                        moveUpContentDescription = "Move up",
                        moveDownContentDescription = "Move down",
                        shapes = it,
                    )
                },
            ),
        )
    }
}
