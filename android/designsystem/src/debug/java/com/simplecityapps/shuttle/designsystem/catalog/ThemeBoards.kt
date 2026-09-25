package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.theme.LocalS2ThemeSettings
import com.simplecityapps.shuttle.designsystem.theme.S2Accent
import com.simplecityapps.shuttle.designsystem.theme.S2Contrast
import com.simplecityapps.shuttle.designsystem.theme.accentColorScheme
import com.simplecityapps.shuttle.designsystem.theme.artworkColorScheme

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
    Board(
        width,
        listOf(
            BoardSection("Type scale") { TypeSamples(regular) },
            BoardSection("Emphasized") { TypeSamples(emphasized) },
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
private fun MorphStrip(name: String, from: RoundedPolygon, to: RoundedPolygon) {
    val morph = Morph(from, to)
    val color = MaterialTheme.colorScheme.tertiary
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0f, 0.5f, 1f).forEach { progress ->
            Canvas(Modifier.size(48.dp)) {
                scale(size.width, size.height, pivot = Offset.Zero) { drawPath(morph.toPath(progress), color) }
            }
        }
        Caption(name, Modifier.width(120.dp))
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
            BoardSection("MaterialShapes S2 uses (non-content only)") {
                ShapeSamples(
                    ArtworkPlaceholder.entries.map { it.name to it.polygon.toShape() } +
                        listOf(
                            "Playlist mask" to MaterialShapes.Cookie12Sided.toShape(),
                            "Empty" to MaterialShapes.Cookie9Sided.toShape(),
                            "Error" to MaterialShapes.Burst.toShape(),
                        ),
                )
            },
            BoardSection("Morphs: start, mid, end") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphStrip("Press: circle → square", MaterialShapes.Circle, MaterialShapes.Square)
                    MorphStrip("Play → pause", MaterialShapes.Cookie9Sided, MaterialShapes.Square)
                    MorphStrip("Loading indicator", MaterialShapes.SoftBurst, MaterialShapes.Cookie9Sided)
                }
            },
        ),
    )
}
