package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.component.ActionsSheetContent
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2FilterChip
import com.simplecityapps.shuttle.designsystem.component.S2InputChip
import com.simplecityapps.shuttle.designsystem.component.S2MenuContent
import com.simplecityapps.shuttle.designsystem.component.S2SortChip

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun ChipBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Sort: field and order") {
                Chips {
                    S2SortChip("Title", ascending = true, onClick = {})
                    S2SortChip("Date added", ascending = false, onClick = {})
                }
            },
            BoardSection("Filters: selected, unselected") {
                Chips {
                    S2FilterChip("Downloaded", selected = true, onClick = {})
                    S2FilterChip("Favourites", selected = false, onClick = {})
                }
            },
            BoardSection("Source filters (removable)") {
                Chips {
                    S2InputChip("Jellyfin", onRemove = {})
                    S2InputChip("Local", onRemove = {})
                }
            },
            BoardSection("Disabled") {
                Chips {
                    S2SortChip("Title", ascending = true, onClick = {}, enabled = false)
                    S2FilterChip("Downloaded", selected = true, onClick = {}, enabled = false)
                    S2FilterChip("Favourites", selected = false, onClick = {}, enabled = false)
                }
            },
        ),
    )
}

private val songActions = listOf(
    S2Action("Play next", {}, Icons.AutoMirrored.Rounded.PlaylistPlay),
    S2Action("Add to queue", {}, Icons.AutoMirrored.Rounded.QueueMusic),
    S2Action("Add to playlist", {}, Icons.AutoMirrored.Rounded.PlaylistAdd),
    S2Action("Go to album", {}, Icons.Rounded.Album),
    S2Action("Go to artist", {}, Icons.Rounded.Person),
    S2Action("Song info", {}, Icons.Rounded.Info),
    S2Action("Edit tags", {}, Icons.Rounded.Edit),
    S2Action("Exclude", {}, Icons.Rounded.Block),
    S2Action("Delete", {}, Icons.Rounded.Delete, destructive = true),
)

@Composable
fun MenuBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Overflow: icons, groups, destructive item") {
                S2MenuContent(
                    groups = listOf(
                        listOf(
                            S2Action("Play", {}, Icons.Rounded.PlayArrow),
                            S2Action("Shuffle", {}, Icons.Rounded.Shuffle),
                            S2Action("Add to queue", {}, Icons.AutoMirrored.Rounded.QueueMusic),
                        ),
                        listOf(S2Action("Add to playlist", {}, Icons.AutoMirrored.Rounded.PlaylistAdd), S2Action("Edit tags", {}, Icons.Rounded.Edit)),
                        listOf(S2Action("Delete", {}, Icons.Rounded.Delete, destructive = true)),
                    ),
                    onDismissRequest = {},
                    modifier = Modifier.width(240.dp),
                )
            },
            BoardSection("Sort: checked field and order") {
                S2MenuContent(
                    groups = listOf(
                        listOf(
                            S2Action("Title", {}, selected = false),
                            S2Action("Artist", {}, selected = true),
                            S2Action("Album", {}, selected = false),
                            S2Action("Date added", {}, selected = false),
                        ),
                        listOf(S2Action("Ascending", {}, selected = true), S2Action("Descending", {}, selected = false)),
                    ),
                    onDismissRequest = {},
                    modifier = Modifier.width(240.dp),
                )
            },
        ),
    )
}

/** The sheet's container as `ModalBottomSheet` draws it; the real sheet opens in its own window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Surface(shape = BottomSheetDefaults.ExpandedShape, color = BottomSheetDefaults.ContainerColor, modifier = Modifier.fillMaxWidth()) {
        Column {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            content()
        }
    }
}

@Composable
fun ActionsSheetBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Song: header with artwork, destructive item") {
                SheetFrame {
                    ActionsSheetContent(
                        title = "Paranoid Android",
                        subtitle = "Radiohead · OK Computer",
                        artwork = { Artwork(ArtworkPlaceholder.Song, image = { SampleArt() }) },
                        actions = songActions,
                        onDismissRequest = {},
                    )
                }
            },
            BoardSection("Playlist: long title, Remove") {
                SheetFrame {
                    ActionsSheetContent(
                        title = "Songs for a long drive through the mountains at night",
                        subtitle = "48 songs",
                        artwork = { Artwork(ArtworkPlaceholder.Playlist, shape = ArtworkShape.Scalloped, image = { SampleArt(1) }) },
                        actions = listOf(
                            S2Action("Play", {}, Icons.Rounded.PlayArrow),
                            S2Action("Shuffle", {}, Icons.Rounded.Shuffle),
                            S2Action("Rename", {}, Icons.Rounded.Edit),
                            S2Action("Remove", {}, Icons.Rounded.RemoveCircleOutline, destructive = true),
                        ),
                        onDismissRequest = {},
                    )
                }
            },
        ),
    )
}
