package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.theme.ContinuousRoundedCornerShape
import com.simplecityapps.shuttle.designsystem.theme.LocalS2ThemeSettings
import com.simplecityapps.shuttle.designsystem.theme.S2Accent
import com.simplecityapps.shuttle.designsystem.theme.S2ContentWidth
import com.simplecityapps.shuttle.designsystem.theme.S2Contrast
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.designsystem.theme.accentColorScheme
import com.simplecityapps.shuttle.designsystem.theme.artworkColorScheme
import com.simplecityapps.shuttle.designsystem.theme.groupHeader
import com.simplecityapps.shuttle.designsystem.theme.heroSubtitle
import com.simplecityapps.shuttle.designsystem.theme.heroTitle
import com.simplecityapps.shuttle.designsystem.theme.leadSectionTitle
import com.simplecityapps.shuttle.designsystem.theme.playerSubtitle
import com.simplecityapps.shuttle.designsystem.theme.playerTitle
import com.simplecityapps.shuttle.designsystem.theme.rowMeta
import com.simplecityapps.shuttle.designsystem.theme.rowSubtitle
import com.simplecityapps.shuttle.designsystem.theme.rowTitle
import com.simplecityapps.shuttle.designsystem.theme.screenTitle
import com.simplecityapps.shuttle.designsystem.theme.sectionTitle
import com.simplecityapps.shuttle.designsystem.theme.supporting
import com.simplecityapps.shuttle.designsystem.theme.tileSubtitle
import com.simplecityapps.shuttle.designsystem.theme.tileTitle
import com.simplecityapps.shuttle.designsystem.theme.time

private class RolePair(val name: String, val color: Color, val onColor: Color)

private fun ColorScheme.rolePairs() = listOf(
    RolePair("primary", primary, onPrimary),
    RolePair("primaryContainer", primaryContainer, onPrimaryContainer),
    RolePair("secondary", secondary, onSecondary),
    RolePair("secondaryContainer", secondaryContainer, onSecondaryContainer),
    RolePair("tertiary", tertiary, onTertiary),
    RolePair("tertiaryContainer", tertiaryContainer, onTertiaryContainer),
    RolePair("error", error, onError),
    RolePair("errorContainer", errorContainer, onErrorContainer),
    RolePair("primaryFixed", primaryFixed, onPrimaryFixed),
    RolePair("primaryFixedDim", primaryFixedDim, onPrimaryFixedVariant),
    RolePair("secondaryFixed", secondaryFixed, onSecondaryFixed),
    RolePair("tertiaryFixed", tertiaryFixed, onTertiaryFixed),
    RolePair("surface", surface, onSurface),
    RolePair("surfaceVariant", surfaceVariant, onSurfaceVariant),
    RolePair("surfaceDim", surfaceDim, onSurface),
    RolePair("surfaceBright", surfaceBright, onSurface),
    RolePair("containerLowest", surfaceContainerLowest, onSurface),
    RolePair("containerLow", surfaceContainerLow, onSurface),
    RolePair("container", surfaceContainer, onSurface),
    RolePair("containerHigh", surfaceContainerHigh, onSurface),
    RolePair("containerHighest", surfaceContainerHighest, onSurface),
    RolePair("inverseSurface", inverseSurface, inverseOnSurface),
    RolePair("inversePrimary", inversePrimary, inverseSurface),
    RolePair("outline", outline, surface),
    RolePair("outlineVariant", outlineVariant, onSurface),
    RolePair("background", background, onBackground),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(pairs: List<RolePair>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 2) {
        pairs.forEach { pair ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(pair.color, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(pair.name, style = MaterialTheme.typography.labelMedium, color = pair.onColor)
            }
        }
    }
}

@Composable
fun ThemeColourBoard(width: BoardWidth) {
    val settings = LocalS2ThemeSettings.current
    val seed = LocalCatalogScheme.current.seed
    val highContrast = seed?.let { artworkColorScheme(it, settings.isDark, contrast = S2Contrast.High) }
        ?: accentColorScheme(settings.accent, settings.isDark, S2Contrast.High)
    Board(
        width,
        listOf(
            BoardSection("Roles, each on its on-colour") { Swatches(MaterialTheme.colorScheme.rolePairs()) },
            BoardSection("High contrast") { Swatches(highContrast.rolePairs().take(8) + highContrast.rolePairs().filter { it.name == "surfaceVariant" }) },
            BoardSection("Root accents: primary, secondary, tertiary containers") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    S2Accent.entries.forEach { accent ->
                        val scheme = accentColorScheme(accent, settings.isDark)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row {
                                listOf(scheme.primary, scheme.secondaryContainer, scheme.tertiaryContainer).forEach {
                                    Box(Modifier.size(18.dp).background(it))
                                }
                            }
                            Caption(accent.name)
                        }
                    }
                }
            },
        ),
    )
}

private val typeStyles: @Composable () -> List<Pair<String, TextStyle>> = {
    val t = MaterialTheme.typography
    listOf(
        "Display large" to t.displayLarge, "Display medium" to t.displayMedium, "Display small" to t.displaySmall,
        "Headline large" to t.headlineLarge, "Headline medium" to t.headlineMedium, "Headline small" to t.headlineSmall,
        "Title large" to t.titleLarge, "Title medium" to t.titleMedium, "Title small" to t.titleSmall,
        "Body large" to t.bodyLarge, "Body medium" to t.bodyMedium, "Body small" to t.bodySmall,
        "Label large" to t.labelLarge, "Label medium" to t.labelMedium, "Label small" to t.labelSmall,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val emphasizedStyles: @Composable () -> List<Pair<String, TextStyle>> = {
    val t = MaterialTheme.typography
    listOf(
        "Display large" to t.displayLargeEmphasized, "Display medium" to t.displayMediumEmphasized, "Display small" to t.displaySmallEmphasized,
        "Headline large" to t.headlineLargeEmphasized, "Headline medium" to t.headlineMediumEmphasized, "Headline small" to t.headlineSmallEmphasized,
        "Title large" to t.titleLargeEmphasized, "Title medium" to t.titleMediumEmphasized, "Title small" to t.titleSmallEmphasized,
        "Body large" to t.bodyLargeEmphasized, "Body medium" to t.bodyMediumEmphasized, "Body small" to t.bodySmallEmphasized,
        "Label large" to t.labelLargeEmphasized, "Label medium" to t.labelMediumEmphasized, "Label small" to t.labelSmallEmphasized,
    )
}

private val roleStyles: @Composable () -> List<Pair<String, TextStyle>> = {
    val t = MaterialTheme.typography
    listOf(
        "heroTitle" to t.heroTitle, "heroSubtitle" to t.heroSubtitle,
        "playerTitle" to t.playerTitle, "playerSubtitle" to t.playerSubtitle,
        "screenTitle" to t.screenTitle, "leadSectionTitle" to t.leadSectionTitle, "sectionTitle" to t.sectionTitle,
        "groupHeader" to t.groupHeader, "rowTitle" to t.rowTitle, "rowSubtitle" to t.rowSubtitle, "rowMeta" to t.rowMeta,
        "tileTitle" to t.tileTitle, "tileSubtitle" to t.tileSubtitle, "supporting" to t.supporting, "time" to t.time,
    )
}

@Composable
private fun TypeSamples(styles: List<Pair<String, TextStyle>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        styles.forEach { (name, style) -> Text(name, style = style, maxLines = 1) }
    }
}

@Composable
fun ThemeTypeBoard(width: BoardWidth) {
    val regular = typeStyles()
    val emphasized = emphasizedStyles()
    val roles = roleStyles()
    Board(
        width,
        listOf(
            BoardSection("Type scale") { TypeSamples(regular) },
            BoardSection("Emphasized") { TypeSamples(emphasized) },
            BoardSection("Roles") { TypeSamples(roles) },
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeSamples(shapes: List<Pair<String, Shape>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shapes.forEach { (name, shape) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer))
                Caption(name)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeShapeBoard(width: BoardWidth) {
    val s = MaterialTheme.shapes
    Board(
        width,
        listOf(
            BoardSection("Shape scale") {
                ShapeSamples(
                    listOf(
                        "extraSmall" to s.extraSmall,
                        "small" to s.small,
                        "medium" to s.medium,
                        "large" to s.large,
                        "largeIncreased" to s.largeIncreased,
                        "extraLarge" to s.extraLarge,
                        "extraLargeIncr." to s.extraLargeIncreased,
                        "extraExtraLarge" to s.extraExtraLarge,
                    ),
                )
            },
            BoardSection("Continuous (top) against circular corners") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShapeSamples(
                        listOf(
                            "16 dp" to ContinuousRoundedCornerShape(16.dp),
                            "24 dp" to ContinuousRoundedCornerShape(24.dp),
                            "Capsule" to ContinuousRoundedCornerShape(50),
                            "Top only" to ContinuousRoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        ),
                    )
                    ShapeSamples(
                        listOf(
                            "16 dp" to RoundedCornerShape(16.dp),
                            "24 dp" to RoundedCornerShape(24.dp),
                            "Capsule" to RoundedCornerShape(50),
                            "Top only" to RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        ),
                    )
                }
            },
        ),
    )
}

@Composable
private fun DimensionBar(name: String, value: Dp) {
    Column {
        Box(Modifier.widthIn(max = value).fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.primary))
        Caption("$name ${value.value.toInt()} dp")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeDimensionBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Spacing") {
                Column(verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
                    listOf(
                        "tiny" to S2Spacing.tiny,
                        "xsmall" to S2Spacing.xsmall,
                        "small" to S2Spacing.small,
                        "smallMedium" to S2Spacing.smallMedium,
                        "medium" to S2Spacing.medium,
                        "large" to S2Spacing.large,
                        "xlarge" to S2Spacing.xlarge,
                    ).forEach { (name, value) -> DimensionBar(name, value) }
                }
            },
            BoardSection("Icon size") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(S2Spacing.large), verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
                    listOf("small" to S2IconSize.small, "medium" to S2IconSize.medium, "hero" to S2IconSize.hero).forEach { (name, size) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(size))
                            Caption("$name ${size.value.toInt()} dp")
                        }
                    }
                }
            },
            BoardSection("Touch target") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(S2TouchTarget.minimum).border(1.dp, MaterialTheme.colorScheme.outline),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(S2IconSize.medium))
                    }
                    Caption("minimum ${S2TouchTarget.minimum.value.toInt()} dp")
                }
            },
            BoardSection("Content width (capped at the board)") {
                Column(verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
                    listOf(
                        "dialogMinimum" to S2ContentWidth.dialogMinimum,
                        "readable" to S2ContentWidth.readable,
                        "dialogMaximum" to S2ContentWidth.dialogMaximum,
                        "maximum" to S2ContentWidth.maximum,
                    ).forEach { (name, value) -> DimensionBar(name, value) }
                }
            },
        ),
    )
}
