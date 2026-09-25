package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtistRow
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.PlaylistRow
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongOfflineState
import com.simplecityapps.shuttle.designsystem.component.SongRow

@Composable
private fun ArtworkRow(sizes: List<ArtworkSize>, shape: ArtworkShape = ArtworkShape.Rounded, loading: Boolean = false, loaded: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        sizes.forEachIndexed { index, size ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(
                    placeholder = if (shape == ArtworkShape.Circle) ArtworkPlaceholder.Artist else ArtworkPlaceholder.Album,
                    size = size,
                    shape = shape,
                    loading = loading,
                    image = if (loaded) {
                        { SampleArt(index) }
                    } else {
                        null
                    },
                )
                Caption("${size.dp.value.toInt()}dp")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtworkBoard(width: BoardWidth) {
    val rowSizes = listOf(ArtworkSize.Small, ArtworkSize.Medium, ArtworkSize.Grid)
    Board(
        width,
        listOf(
            BoardSection("Rounded, loaded") { ArtworkRow(rowSizes) },
            BoardSection("Circle (artist), loaded") { ArtworkRow(rowSizes, ArtworkShape.Circle) },
            BoardSection("Loading") { ArtworkRow(rowSizes, loading = true, loaded = false) },
            BoardSection("Hero") { ArtworkRow(listOf(ArtworkSize.Hero)) },
            BoardSection("Placeholder per media type") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArtworkPlaceholder.entries.forEach { placeholder ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Artwork(
                                placeholder,
                                shape = if (placeholder == ArtworkPlaceholder.Artist) ArtworkShape.Circle else ArtworkShape.Rounded,
                            )
                            Caption(placeholder.name)
                        }
                    }
                }
            },
        ),
    )
}

@Composable
private fun SongArt() = Artwork(ArtworkPlaceholder.Song, size = ArtworkSize.Medium, image = { SampleArt() })

@Composable
fun SongRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Default") {
                SongRow("Paranoid Android", "Radiohead · OK Computer", {}, artwork = { SongArt() }, duration = "6:27", onMore = {})
            },
            BoardSection("Track number (album)") {
                SongRow("Karma Police", "Radiohead", {}, trackNumber = 6, duration = "4:24", onMore = {})
            },
            BoardSection("Playing") {
                SongRow("Paranoid Android", "Radiohead · OK Computer", {}, artwork = { SongArt() }, duration = "6:27", playing = true, onMore = {})
            },
            BoardSection("Selected") {
                SongRow("Let Down", "Radiohead · OK Computer", {}, artwork = { SongArt() }, duration = "4:59", selected = true, onMore = {})
            },
            BoardSection("Missing file (disabled)") {
                SongRow("Lucky", "Radiohead · OK Computer", {}, artwork = { SongArt() }, duration = "4:19", enabled = false, onMore = {})
            },
            BoardSection("Downloading") {
                SongRow("No Surprises", "Radiohead · OK Computer", {}, artwork = { SongArt() }, duration = "3:48", offlineState = SongOfflineState.Downloading, onMore = {})
            },
            BoardSection("Offline") {
                SongRow("The Tourist", "Radiohead · OK Computer", {}, artwork = { SongArt() }, duration = "5:24", offlineState = SongOfflineState.Offline, onMore = {})
            },
            BoardSection("Long text") {
                SongRow(
                    "A song title long enough to run out of room in a compact row",
                    "An artist with a long name · An album with an even longer name",
                    {},
                    artwork = { SongArt() },
                    duration = "12:07",
                    onMore = {},
                )
            },
        ),
    )
}

@Composable
private fun AlbumArt() = Artwork(ArtworkPlaceholder.Album, image = { SampleArt(1) })

@Composable
fun AlbumRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Default") { AlbumRow("OK Computer", "Radiohead", {}, artwork = { AlbumArt() }, meta = "1997", onMore = {}) },
            BoardSection("Selected") { AlbumRow("Kid A", "Radiohead", {}, artwork = { AlbumArt() }, meta = "2000", selected = true, onMore = {}) },
            BoardSection("No artwork") { AlbumRow("Amnesiac", "Radiohead", {}, artwork = { Artwork(ArtworkPlaceholder.Album) }, meta = "2001", onMore = {}) },
            BoardSection("Long text") {
                AlbumRow(
                    "An album title long enough to run out of room in a compact row",
                    "An artist with a long name",
                    {},
                    artwork = { AlbumArt() },
                    meta = "2016",
                    onMore = {},
                )
            },
        ),
    )
}

@Composable
private fun ArtistArt(loaded: Boolean = true) = Artwork(
    ArtworkPlaceholder.Artist,
    shape = ArtworkShape.Circle,
    image = if (loaded) {
        { SampleArt(2) }
    } else {
        null
    },
)

@Composable
fun ArtistRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Default") { ArtistRow("Radiohead", {}, summary = "9 albums · 102 songs", artwork = { ArtistArt() }, onMore = {}) },
            BoardSection("Selected") { ArtistRow("Portishead", {}, summary = "3 albums · 33 songs", artwork = { ArtistArt() }, selected = true, onMore = {}) },
            BoardSection("No image") { ArtistRow("Massive Attack", {}, summary = "5 albums · 51 songs", artwork = { ArtistArt(loaded = false) }, onMore = {}) },
        ),
    )
}

@Composable
fun PlaylistRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("User playlist") { PlaylistRow("Road trip", {}, summary = "48 songs", artwork = { Artwork(ArtworkPlaceholder.Playlist) }, onMore = {}) },
            BoardSection("Smart playlist") { PlaylistRow("Recently added", {}, summary = "120 songs", artwork = { Artwork(ArtworkPlaceholder.SmartPlaylist) }, onMore = {}) },
            BoardSection("Empty") { PlaylistRow("New playlist", {}, summary = "No songs", artwork = { Artwork(ArtworkPlaceholder.Playlist) }, onMore = {}) },
            BoardSection("Selected") { PlaylistRow("Focus", {}, summary = "22 songs", artwork = { Artwork(ArtworkPlaceholder.Playlist) }, selected = true, onMore = {}) },
        ),
    )
}

@Composable
fun SectionHeaderBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("With action") { SectionHeader("Recently added", action = "See all") },
            BoardSection("Plain") { SectionHeader("Albums") },
            BoardSection("Sticky letter header") {
                Column {
                    SectionHeader("A")
                    ArtistRow("Air", {}, summary = "6 albums", artwork = { ArtistArt() })
                    ArtistRow("Aphex Twin", {}, summary = "11 albums", artwork = { ArtistArt(loaded = false) })
                }
            },
        ),
    )
}
