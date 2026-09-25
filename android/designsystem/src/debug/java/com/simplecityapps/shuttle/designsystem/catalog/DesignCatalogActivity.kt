package com.simplecityapps.shuttle.designsystem.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.theme.S2Contrast

/**
 * The design system catalogue on device, for what the recorded boards can't show: motion, touch,
 * dynamic colour. Debug builds only, with its own launcher icon ("S2 Catalog").
 *
 * `adb shell am start -n com.simplecityapps.shuttle.dev/com.simplecityapps.shuttle.designsystem.catalog.DesignCatalogActivity --es board row-song`
 * opens one board directly.
 */
class DesignCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val board = intent.getStringExtra(EXTRA_BOARD)
        setContent { CatalogScreen(initialBoard = board) }
    }

    companion object {
        const val EXTRA_BOARD = "board"
    }
}

private val FontScales = listOf(1f, 1.3f, 2f)

@Composable
private fun CatalogScreen(initialBoard: String?) {
    var boardId by rememberSaveable { mutableStateOf(CatalogEntries.firstOrNull { it.id == initialBoard }?.id) }
    val systemDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(systemDark) }
    var scheme by rememberSaveable { mutableStateOf(CatalogScheme.Brand) }
    var contrast by rememberSaveable { mutableStateOf(S2Contrast.Default) }
    var fontScale by rememberSaveable { mutableFloatStateOf(1f) }
    var slowMotion by rememberSaveable { mutableStateOf(false) }

    val entry = CatalogEntries.firstOrNull { it.id == boardId }
    BackHandler(enabled = entry != null) { boardId = null }

    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        CatalogTheme(scheme = scheme, darkTheme = dark, contrast = contrast, motionSlowdown = if (slowMotion) 5f else 1f) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    if (entry != null) {
                        IconButton(onClick = { boardId = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Text(
                        entry?.title ?: "S2 catalogue",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
                Controls(
                    dark = dark,
                    onDark = { dark = it },
                    scheme = scheme,
                    onScheme = { scheme = it },
                    contrast = contrast,
                    onContrast = { contrast = it },
                    fontScale = fontScale,
                    onFontScale = { fontScale = it },
                    slowMotion = slowMotion,
                    onSlowMotion = { slowMotion = it },
                )
                HorizontalDivider()
                if (entry == null) {
                    BoardList(onOpen = { boardId = it.id })
                } else {
                    val width = if (LocalConfiguration.current.screenWidthDp >= 840) BoardWidth.Expanded else BoardWidth.Compact
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        entry.board(width)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    scheme: CatalogScheme,
    onScheme: (CatalogScheme) -> Unit,
    contrast: S2Contrast,
    onContrast: (S2Contrast) -> Unit,
    fontScale: Float,
    onFontScale: (Float) -> Unit,
    slowMotion: Boolean,
    onSlowMotion: (Boolean) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = dark, onClick = { onDark(!dark) }, label = { Text("Dark") })
        FilterChip(selected = slowMotion, onClick = { onSlowMotion(!slowMotion) }, label = { Text("Motion 5× slower") })
        CycleChip("Scheme", scheme, CatalogScheme.entries, onScheme)
        CycleChip("Contrast", contrast, S2Contrast.entries, onContrast)
        CycleChip("Font", fontScale, FontScales, onFontScale) { "$it×" }
    }
}

/** A chip that steps through [values] on each tap. */
@Composable
private fun <T> CycleChip(label: String, value: T, values: List<T>, onChange: (T) -> Unit, format: (T) -> String = { it.toString() }) {
    FilterChip(
        selected = value != values.first(),
        onClick = { onChange(values[(values.indexOf(value) + 1) % values.size]) },
        label = { Text("$label: ${format(value)}") },
    )
}

@Composable
private fun BoardList(onOpen: (CatalogEntry) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(CatalogEntries, key = { it.id }) { entry ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(entry) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    entry.states.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
