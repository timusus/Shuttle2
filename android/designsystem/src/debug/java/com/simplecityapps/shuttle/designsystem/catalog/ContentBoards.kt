package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtistRow
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.FolderEntryKind
import com.simplecityapps.shuttle.designsystem.component.FolderRow
import com.simplecityapps.shuttle.designsystem.component.GenreRow
import com.simplecityapps.shuttle.designsystem.component.GridTile
import com.simplecityapps.shuttle.designsystem.component.PlaylistRow
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongOfflineState
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.fixtures.SampleAlbum
import com.simplecityapps.shuttle.fixtures.SampleArtist
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.fixtures.SampleSong

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
            BoardSection("Scalloped (playlist mask option), loaded and placeholder") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    Artwork(ArtworkPlaceholder.Playlist, size = ArtworkSize.Grid, shape = ArtworkShape.Scalloped, image = { SampleArt(2) })
                    Artwork(ArtworkPlaceholder.Playlist, size = ArtworkSize.Grid, shape = ArtworkShape.Scalloped)
                }
            },
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

private val nightBus = SampleLibrary.album("night-bus-frequencies")

private val SampleSong.subtitle: String get() = "$artist · $album"

private val SampleArtist.summary: String get() = "${albumCount(albums.size)} · ${songCount(songCount)}"

@Composable
private fun SongArt(albumId: String = nightBus.id) = Artwork(ArtworkPlaceholder.Song, size = ArtworkSize.Medium, image = { SampleArt(albumId) })

@Composable
fun SongRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Default") {
                val song = nightBus.songs[0]
                SongRow(song.title, song.subtitle, {}, artwork = { SongArt() }, duration = song.duration, onMore = {})
            },
            BoardSection("Track number (album)") {
                val song = nightBus.songs[5]
                SongRow(song.title, song.artist, {}, trackNumber = song.track, duration = song.duration, onMore = {})
            },
            BoardSection("Playing") {
                val song = nightBus.songs[0]
                SongRow(song.title, song.subtitle, {}, artwork = { SongArt() }, duration = song.duration, playing = true, onMore = {})
            },
            BoardSection("Selected") {
                val song = nightBus.songs[1]
                SongRow(song.title, song.subtitle, {}, artwork = { SongArt() }, duration = song.duration, selected = true, onMore = {})
            },
            BoardSection("Missing file (disabled)") {
                val song = nightBus.songs[2]
                SongRow(song.title, song.subtitle, {}, artwork = { SongArt() }, duration = song.duration, enabled = false, onMore = {})
            },
            BoardSection("Downloading") {
                val song = nightBus.songs[3]
                SongRow(song.title, song.subtitle, {}, artwork = { SongArt() }, duration = song.duration, offlineState = SongOfflineState.Downloading, onMore = {})
            },
            BoardSection("Offline") {
                val song = nightBus.songs[4]
                SongRow(song.title, song.subtitle, {}, artwork = { SongArt() }, duration = song.duration, offlineState = SongOfflineState.Offline, onMore = {})
            },
            BoardSection("Long text") {
                val song = SampleLibrary.longTitleAlbum.songs.maxBy { it.title.length }
                SongRow(song.title, song.subtitle, {}, artwork = { SongArt(song.albumId) }, duration = song.duration, onMore = {})
            },
        ),
    )
}

@Composable
private fun AlbumRow(album: SampleAlbum, artwork: Boolean = true, selected: Boolean = false) = AlbumRow(
    album.title,
    album.artist,
    {},
    artwork = { Artwork(ArtworkPlaceholder.Album, image = if (artwork) ({ SampleArt(album.id) }) else null) },
    meta = album.year.toString(),
    selected = selected,
    onMore = {},
)

@Composable
fun AlbumRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Default") { AlbumRow(nightBus) },
            BoardSection("Selected") { AlbumRow(SampleLibrary.album("phase-garden"), selected = true) },
            BoardSection("No artwork") { AlbumRow(SampleLibrary.album("signal-room"), artwork = false) },
            BoardSection("Long text") { AlbumRow(SampleLibrary.longTitleAlbum) },
        ),
    )
}

@Composable
private fun ArtistRow(artist: SampleArtist, loaded: Boolean = true, selected: Boolean = false, summary: String = artist.summary) = ArtistRow(
    artist.name,
    {},
    summary = summary,
    artwork = {
        Artwork(
            ArtworkPlaceholder.Artist,
            shape = ArtworkShape.Circle,
            image = if (loaded) ({ SampleArt(artist.coverAlbumId) }) else null,
        )
    },
    selected = selected,
    onMore = {},
)

@Composable
fun ArtistRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Default") { ArtistRow(SampleLibrary.artist("Juniper Static")) },
            BoardSection("Selected") { ArtistRow(SampleLibrary.artist("Marlow Vane"), selected = true) },
            BoardSection("No image") { ArtistRow(SampleLibrary.artist("The Tin Orchards"), loaded = false) },
        ),
    )
}

@Composable
fun PlaylistRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("User playlist") { PlaylistRow("Road Trip", {}, summary = songCount(SampleLibrary.playlist("Road Trip").songs.size), artwork = { Artwork(ArtworkPlaceholder.Playlist) }, onMore = {}) },
            BoardSection("Smart playlist") { PlaylistRow("Recently added", {}, summary = "120 songs", artwork = { Artwork(ArtworkPlaceholder.SmartPlaylist) }, onMore = {}) },
            BoardSection("Empty") { PlaylistRow("New playlist", {}, summary = "No songs", artwork = { Artwork(ArtworkPlaceholder.Playlist) }, onMore = {}) },
            BoardSection("Selected") { PlaylistRow("Focus", {}, summary = songCount(SampleLibrary.playlist("Focus").songs.size), artwork = { Artwork(ArtworkPlaceholder.Playlist) }, selected = true, onMore = {}) },
            BoardSection("Scalloped artwork (mask option)") {
                PlaylistRow("Late Night", {}, summary = songCount(SampleLibrary.playlist("Late Night").songs.size), artwork = { Artwork(ArtworkPlaceholder.Playlist, shape = ArtworkShape.Scalloped, image = { SampleArt(2) }) }, onMore = {})
            },
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
                    val artist = SampleLibrary.artist("Oda Kestrel Quartet")
                    SectionHeader(artist.name.take(1))
                    ArtistRow(artist, summary = albumCount(artist.albums.size))
                }
            },
        ),
    )
}

@Composable
fun GenreRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Default") { GenreRow("Electronic", {}, songCount = songCount(SampleLibrary.genres.first { it.name == "Electronic" }.songs.size), artwork = { Artwork(ArtworkPlaceholder.Genre) }, onMore = {}) },
            BoardSection("Selected") { GenreRow("Shoegaze", {}, songCount = songCount(SampleLibrary.genres.first { it.name == "Shoegaze" }.songs.size), artwork = { Artwork(ArtworkPlaceholder.Genre) }, selected = true, onMore = {}) },
            BoardSection("One song") { GenreRow("Field recordings", {}, songCount = "1 song", artwork = { Artwork(ArtworkPlaceholder.Genre) }, onMore = {}) },
        ),
    )
}

@Composable
fun FolderRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Folder") { FolderRow("Juniper Static", FolderEntryKind.Folder, {}, summary = "2 folders", onMore = {}) },
            BoardSection("File") {
                val song = nightBus.songs[0]
                FolderRow("0${song.track} ${song.title}.flac", FolderEntryKind.File, {}, summary = song.subtitle, meta = song.duration, artwork = { SongArt() }, onMore = {})
            },
            BoardSection("File, no artwork") { FolderRow("demo take 3.mp3", FolderEntryKind.File, {}, meta = "2:10", onMore = {}) },
            BoardSection("Selected") { FolderRow("Marlow Vane", FolderEntryKind.Folder, {}, summary = "2 folders", selected = true, onMore = {}) },
        ),
    )
}

/** One tile at the width a two-column compact grid gives it. */
@Composable
private fun SampleTile(
    title: String,
    subtitle: String,
    placeholder: ArtworkPlaceholder = ArtworkPlaceholder.Album,
    shape: ArtworkShape = ArtworkShape.Rounded,
    art: String? = null,
    selected: Boolean = false,
    playing: Boolean = false,
) {
    GridTile(
        title = title,
        subtitle = subtitle,
        onClick = {},
        artwork = {
            Artwork(
                placeholder,
                Modifier.fillMaxSize(),
                size = ArtworkSize.Grid,
                shape = shape,
                image = art?.let { albumId -> { SampleArt(albumId) } },
            )
        },
        modifier = Modifier.width(172.dp),
        selected = selected,
        playing = playing,
    )
}

@Composable
private fun AlbumTile(album: SampleAlbum, art: Boolean = true, selected: Boolean = false, playing: Boolean = false) = SampleTile(album.title, album.artist, art = album.id.takeIf { art }, selected = selected, playing = playing)

@Composable
private fun ArtistTile(artist: SampleArtist, art: Boolean = true) = SampleTile(artist.name, albumCount(artist.albums.size), ArtworkPlaceholder.Artist, ArtworkShape.Circle, art = artist.coverAlbumId.takeIf { art })

private fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"

private fun albumCount(count: Int) = if (count == 1) "1 album" else "$count albums"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tiles(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
fun GridTileBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Album and artist") {
                Tiles {
                    AlbumTile(nightBus)
                    ArtistTile(SampleLibrary.artist("Saltmarsh Choir"))
                }
            },
            BoardSection("Playlist (scalloped mask), playing") {
                Tiles {
                    val playlist = SampleLibrary.playlist("Late Night")
                    SampleTile(playlist.name, songCount(playlist.songs.size), ArtworkPlaceholder.Playlist, ArtworkShape.Scalloped, art = "lantern-hours")
                    AlbumTile(SampleLibrary.album("smoke-rings"), playing = true)
                }
            },
            BoardSection("Selected, long text") {
                Tiles {
                    AlbumTile(SampleLibrary.album("soft-focus"), selected = true)
                    AlbumTile(SampleLibrary.longTitleAlbum)
                }
            },
            BoardSection("Placeholder") {
                Tiles {
                    AlbumTile(SampleLibrary.album("undertow"), art = false)
                    ArtistTile(SampleLibrary.artist("Pale Meridian"), art = false)
                }
            },
        ),
    )
}
