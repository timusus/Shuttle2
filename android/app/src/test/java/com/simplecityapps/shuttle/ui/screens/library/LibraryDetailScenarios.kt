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
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.ArtistSongComparator
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailUiState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailUiState.SongSection
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailUiState

/** The album [songs] belong to, keyed the way the songs group, so it unfolds to them. */
fun albumOf(songs: List<Song>, year: Int = 2021) = createAlbum(
    name = songs.first().album.orEmpty(),
    albumArtist = songs.first().albumArtist,
    songCount = songs.size,
    year = year,
    groupKey = songs.first().albumGroupKey,
)

/** Three songs on "Phase Garden" by Juniper Static, tracks 1 to 3. */
fun phaseGardenSongs(disc: Int = 1) = listOf("Chlorophyll Loop", "Soft Machines at Dawn", "Petal Arithmetic").mapIndexed { index, name ->
    createSong(id = index + 1L + (disc - 1) * 100, name = name, albumArtist = "Juniper Static", album = "Phase Garden", track = index + 1, disc = disc, duration = 240_000)
}

fun readyAlbumDetail(
    album: Album = createAlbum(name = "Phase Garden", albumArtist = "Juniper Static", songCount = 3, year = 2021),
    songs: List<Song> = phaseGardenSongs(),
    currentSong: Song? = null,
    moreByArtist: List<Album> = emptyList(),
    moreByArtistNames: List<String> = if (moreByArtist.isEmpty()) emptyList() else listOfNotNull(album.albumArtist),
) = AlbumDetailUiState(album = album, songs = songs, currentSong = currentSong, loadingState = AlbumDetailUiState.LoadingState.Ready, moreByArtist = moreByArtist, moreByArtistNames = moreByArtistNames)

val loadingAlbumDetail = AlbumDetailUiState(loadingState = AlbumDetailUiState.LoadingState.Loading)

val missingAlbumDetail = AlbumDetailUiState(album = null, loadingState = AlbumDetailUiState.LoadingState.Empty)

/**
 * An artist's detail as the ViewModel derives it: for an album [sortOrder] (the default), one section per album in
 * [albums]' order with its songs in track order, then the songs on none of them; for a flat one, every song in one section.
 */
fun readyAlbumArtistDetail(
    artist: AlbumArtist = createAlbumArtist(name = "Juniper Static"),
    songs: List<Song> = phaseGardenSongs(),
    albums: List<Album> = listOf(albumOf(songs)),
    sortOrder: ArtistSongSortOrder = ArtistSongSortOrder.Default,
    expandedAlbums: Set<AlbumGroupKey> = emptySet(),
    currentSong: Song? = null,
    appearsOn: List<Album> = emptyList(),
): AlbumArtistDetailUiState {
    val sections = if (sortOrder.groupsByAlbum) {
        val albumKeys = albums.mapNotNullTo(HashSet()) { it.groupKey }
        val others = songs.filter { it.albumGroupKey !in albumKeys }
        albums.map { album -> SongSection(album, songs.filter { it.albumGroupKey == album.groupKey }.sortedWith(ArtistSongComparator.trackOrder)) }
            .filter { it.songs.isNotEmpty() } + listOfNotNull(others.takeIf { it.isNotEmpty() }?.let { SongSection(null, it) })
    } else {
        listOf(SongSection(null, songs))
    }
    return AlbumArtistDetailUiState(
        albumArtist = artist,
        hero = ArtistHeroArtwork.of(artist, albums, songs, appearsOn),
        albums = albums,
        appearsOn = appearsOn,
        songs = sections.flatMap { it.songs },
        sortOrder = sortOrder,
        sections = sections,
        expandedAlbums = expandedAlbums,
        currentSong = currentSong,
        loadingState = AlbumArtistDetailUiState.LoadingState.Ready,
    )
}

val loadingAlbumArtistDetail = AlbumArtistDetailUiState(loadingState = AlbumArtistDetailUiState.LoadingState.Loading)

fun readyGenreDetail(
    genre: Genre = createGenre(name = "Electronic"),
    albums: List<Album> = listOf(createAlbum(name = "Phase Garden", albumArtist = "Juniper Static")),
    songs: List<Song> = phaseGardenSongs(),
    currentSong: Song? = null,
) = GenreDetailUiState(genre = genre, albums = albums, songs = songs, currentSong = currentSong, loading = false)

val missingGenreDetail = GenreDetailUiState(genre = null, loading = false)

/** [songs] as playlist entries whose ids are 10, 11, 12… in order. */
fun playlistEntries(songs: List<Song> = phaseGardenSongs()) = songs.mapIndexed { index, song -> PlaylistSong(id = 10L + index, sortOrder = index.toLong(), song = song) }

fun readyPlaylistDetail(
    playlist: Playlist = createPlaylist(id = 7, name = "Road trip", songCount = 3),
    songs: List<PlaylistSong> = playlistEntries(),
    selectedIds: Set<Long> = emptySet(),
    currentSong: Song? = null,
) = PlaylistDetailUiState(playlist = playlist, songs = songs, selectedIds = selectedIds, currentSong = currentSong, loading = false)

val loadingPlaylistDetail = PlaylistDetailUiState(loading = true)

fun readySmartPlaylistDetail(
    smartPlaylist: SmartPlaylist = createSmartPlaylist(),
    songs: List<Song> = phaseGardenSongs(),
    currentSong: Song? = null,
) = SmartPlaylistDetailUiState(smartPlaylist = smartPlaylist, songs = songs, currentSong = currentSong, loading = false)

val missingSmartPlaylistDetail = SmartPlaylistDetailUiState(smartPlaylist = null, loading = false)
