package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Song

fun readyAlbumArtistDetail(
    albumArtist: AlbumArtist = createAlbumArtist(),
    albums: List<Album> = listOf(createAlbum()),
    songs: List<Song> = listOf(createSong()),
    currentSong: Song? = null,
    expandedAlbums: Set<AlbumGroupKey> = emptySet(),
) = AlbumArtistDetailUiState(
    albumArtist = albumArtist,
    albums = albums,
    songs = songs,
    currentSong = currentSong,
    expandedAlbums = expandedAlbums,
    loadingState = if (albums.isEmpty() && songs.isEmpty()) {
        AlbumArtistDetailUiState.LoadingState.Empty
    } else {
        AlbumArtistDetailUiState.LoadingState.Ready
    },
)

fun emptyAlbumArtistDetail(
    albumArtist: AlbumArtist = createAlbumArtist(albumCount = 0, songCount = 0),
) = readyAlbumArtistDetail(albumArtist = albumArtist, albums = emptyList(), songs = emptyList())

val loadingAlbumArtistDetail = AlbumArtistDetailUiState(
    loadingState = AlbumArtistDetailUiState.LoadingState.Loading,
)

/**
 * An album whose [AlbumGroupKey] matches [songs], so the screen can group them together.
 *
 * Model factories derive the two keys differently (songs lowercase the album name), so tests that
 * exercise expansion have to take the album's key from a song.
 */
fun albumFor(
    songs: List<Song>,
    name: String = songs.first().album!!,
    year: Int? = 2024,
) = createAlbum(
    name = name,
    songCount = songs.size,
    year = year,
    groupKey = songs.first().albumGroupKey,
)
