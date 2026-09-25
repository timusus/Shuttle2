package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSmartPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailUiState

/** Three songs on "OK Computer" by Radiohead, tracks 1 to 3. */
fun okComputerSongs(disc: Int = 1) = listOf("Airbag", "Paranoid Android", "Subterranean Homesick Alien").mapIndexed { index, name ->
    createSong(id = index + 1L + (disc - 1) * 100, name = name, albumArtist = "Radiohead", album = "OK Computer", track = index + 1, disc = disc, duration = 240_000)
}

fun readyAlbumDetail(
    album: Album = createAlbum(name = "OK Computer", albumArtist = "Radiohead", songCount = 3, year = 1997),
    songs: List<Song> = okComputerSongs(),
    currentSong: Song? = null,
) = AlbumDetailUiState(album = album, songs = songs, currentSong = currentSong, loadingState = AlbumDetailUiState.LoadingState.Ready)

val loadingAlbumDetail = AlbumDetailUiState(loadingState = AlbumDetailUiState.LoadingState.Loading)

val missingAlbumDetail = AlbumDetailUiState(album = null, loadingState = AlbumDetailUiState.LoadingState.Empty)

fun readyAlbumArtistDetail(
    artist: AlbumArtist = createAlbumArtist(name = "Radiohead"),
    albums: List<Album> = listOf(createAlbum(name = "OK Computer", albumArtist = "Radiohead", songCount = 3, year = 1997)),
    songs: List<Song> = okComputerSongs(),
    expandedAlbums: Set<AlbumGroupKey> = emptySet(),
    currentSong: Song? = null,
) = AlbumArtistDetailUiState(
    albumArtist = artist,
    albums = albums,
    songs = songs,
    expandedAlbums = expandedAlbums,
    currentSong = currentSong,
    loadingState = AlbumArtistDetailUiState.LoadingState.Ready,
)

val loadingAlbumArtistDetail = AlbumArtistDetailUiState(loadingState = AlbumArtistDetailUiState.LoadingState.Loading)

fun readyGenreDetail(
    genre: Genre = createGenre(name = "Alternative"),
    albums: List<Album> = listOf(createAlbum(name = "OK Computer", albumArtist = "Radiohead")),
    songs: List<Song> = okComputerSongs(),
    currentSong: Song? = null,
) = GenreDetailUiState(genre = genre, albums = albums, songs = songs, currentSong = currentSong, loading = false)

val missingGenreDetail = GenreDetailUiState(genre = null, loading = false)

/** [songs] as playlist entries whose ids are 10, 11, 12… in order. */
fun playlistEntries(songs: List<Song> = okComputerSongs()) = songs.mapIndexed { index, song -> PlaylistSong(id = 10L + index, sortOrder = index.toLong(), song = song) }

fun readyPlaylistDetail(
    playlist: Playlist = createPlaylist(id = 7, name = "Road trip", songCount = 3),
    songs: List<PlaylistSong> = playlistEntries(),
    selectedIds: Set<Long> = emptySet(),
    currentSong: Song? = null,
) = PlaylistDetailUiState(playlist = playlist, songs = songs, selectedIds = selectedIds, currentSong = currentSong, loading = false)

val loadingPlaylistDetail = PlaylistDetailUiState(loading = true)

fun readySmartPlaylistDetail(
    smartPlaylist: SmartPlaylist = createSmartPlaylist(),
    songs: List<Song> = okComputerSongs(),
    currentSong: Song? = null,
) = SmartPlaylistDetailUiState(smartPlaylist = smartPlaylist, songs = songs, currentSong = currentSong, loading = false)

val missingSmartPlaylistDetail = SmartPlaylistDetailUiState(smartPlaylist = null, loading = false)
