package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonGroup
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2ConnectedButtonGroup
import com.simplecityapps.shuttle.designsystem.component.S2GroupAction
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconToggleButton

@Composable
private fun LabelledRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun ButtonBoard(width: BoardWidth) {
    Board(
        width,
        S2ButtonStyle.entries.map { style ->
            BoardSection("$style: enabled · pressed · focused · disabled") {
                LabelledRow {
                    S2Button("Play", {}, style = style)
                    S2Button("Play", {}, style = style, interactionSource = rememberPressed())
                    S2Button("Play", {}, style = style, interactionSource = rememberFocused())
                    S2Button("Play", {}, style = style, enabled = false)
                }
            }
        } + S2ButtonSize.entries.map { size ->
            BoardSection("Size $size: with and without icon") {
                LabelledRow {
                    S2Button("Play", {}, size = size, icon = Icons.Rounded.PlayArrow)
                    S2Button("Shuffle", {}, size = size, style = S2ButtonStyle.Tonal)
                }
            }
        },
    )
}

@Composable
fun ButtonGroupBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Standard") {
                S2ButtonGroup(S2GroupAction("Play", {}, Icons.Rounded.PlayArrow), Modifier.fillMaxWidth(), listOf(S2GroupAction("Shuffle", {}, Icons.Rounded.Shuffle)))
            },
            BoardSection("Standard, Play pressed") {
                S2ButtonGroup(
                    S2GroupAction("Play", {}, Icons.Rounded.PlayArrow, rememberPressed()),
                    Modifier.fillMaxWidth(),
                    listOf(S2GroupAction("Shuffle", {}, Icons.Rounded.Shuffle)),
                )
            },
            BoardSection("Standard, Shuffle pressed") {
                S2ButtonGroup(
                    S2GroupAction("Play", {}, Icons.Rounded.PlayArrow),
                    Modifier.fillMaxWidth(),
                    listOf(S2GroupAction("Shuffle", {}, Icons.Rounded.Shuffle, rememberPressed())),
                )
            },
            BoardSection("Standard, medium") {
                S2ButtonGroup(
                    S2GroupAction("Play", {}, Icons.Rounded.PlayArrow),
                    Modifier.fillMaxWidth(),
                    listOf(S2GroupAction("Shuffle", {}, Icons.Rounded.Shuffle)),
                    size = S2ButtonSize.Medium,
                )
            },
            BoardSection("Connected: each option checked") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryViews.forEach { selected -> LibraryViewGroup(selected) }
                }
            },
            BoardSection("Connected: trailing pressed, disabled") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pressed = rememberPressed()
                    val sources = remember(pressed) { listOf(MutableInteractionSource(), MutableInteractionSource(), pressed) }
                    LibraryViewGroup(LibraryViews.first(), interactionSources = sources)
                    LibraryViewGroup(LibraryViews.first(), enabled = false)
                }
            },
        ),
    )
}

private val LibraryViews = listOf("Songs", "Albums", "Artists")

@Composable
private fun LibraryViewGroup(
    selected: String,
    enabled: Boolean = true,
    interactionSources: List<MutableInteractionSource>? = null,
) {
    S2ConnectedButtonGroup(
        options = LibraryViews,
        selected = selected,
        onSelect = {},
        label = { it },
        modifier = Modifier.fillMaxWidth(),
        icon = {
            when (it) {
                "Songs" -> Icons.AutoMirrored.Rounded.QueueMusic
                "Albums" -> Icons.Rounded.Album
                else -> Icons.Rounded.Person
            }
        },
        enabled = enabled,
        interactionSources = interactionSources,
    )
}

@Composable
fun IconButtonBoard(width: BoardWidth) {
    Board(
        width,
        S2IconButtonStyle.entries.map { style ->
            BoardSection("$style: enabled · pressed · unchecked · checked · disabled") {
                LabelledRow {
                    S2IconButton(Icons.Rounded.Shuffle, "Shuffle", {}, style = style)
                    S2IconButton(Icons.Rounded.Shuffle, "Shuffle", {}, style = style, interactionSource = rememberPressed())
                    S2IconToggleButton(Icons.Rounded.FavoriteBorder, "Favourite", checked = false, onCheckedChange = {}, checkedIcon = Icons.Rounded.Favorite, style = style)
                    S2IconToggleButton(Icons.Rounded.FavoriteBorder, "Favourite", checked = true, onCheckedChange = {}, checkedIcon = Icons.Rounded.Favorite, style = style)
                    S2IconButton(Icons.Rounded.Shuffle, "Shuffle", {}, style = style, enabled = false)
                }
            }
        } + BoardSection("Sizes: extra small · small · medium · large") {
            LabelledRow {
                S2IconButtonSize.entries.forEach { size ->
                    S2IconButton(Icons.Rounded.PlayArrow, "Play", {}, style = S2IconButtonStyle.Filled, size = size)
                }
            }
        },
    )
}
