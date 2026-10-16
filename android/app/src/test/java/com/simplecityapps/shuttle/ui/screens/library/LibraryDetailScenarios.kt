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
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.buildAlbumRows

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

/**
 * A recital album: a three-movement sonata run, a lone prelude and an untagged encore, in track order, as the
 * ViewModel derives it with Classical display [classical].
 */
fun classicalAlbumDetail(classical: Boolean = true): AlbumDetailUiState {
    fun track(id: Long, name: String, track: Int) = createSong(id = id, name = name, albumArtist = "Mira Calder", album = "Late Sonatas", track = track, duration = 300_000)
    val sonata = listOf("Allegro", "Adagio", "Presto").mapIndexed { index, movement ->
        track(index + 1L, "Sonata No. 3: $movement", index + 1).copy(
            composer = "Ludwig Harrow",
            composerSort = "Harrow, Ludwig",
            conductor = "Ines Varga",
            work = "Sonata No. 3",
            movementName = movement,
            movementNumber = index + 1,
        )
    }
    val prelude = track(4, "Prelude", 4).copy(composer = "Ludwig Harrow", composerSort = "Harrow, Ludwig", work = "Prelude in D", arranger = "T. Okoye")
    val encore = track(5, "Encore", 5)
    val songs = sonata + prelude + encore
    return classicalState(songs, classical)
}

private fun classicalState(songs: List<Song>, classical: Boolean = true, currentSong: Song? = null, toggled: Set<Long> = emptySet()): AlbumDetailUiState {
    val album = albumOf(songs)
    return readyAlbumDetail(album = album, songs = songs, currentSong = currentSong).copy(
        classical = classical,
        rows = if (classical) buildAlbumRows(songs, album.albumArtist, currentSong?.id, toggled) else null,
    )
}

/** One composer's cycle: a playing symphony expanded, the long second work collapsed by default. */
fun symphonyCycleAlbumDetail(): AlbumDetailUiState {
    fun movement(id: Long, work: String, name: String, track: Int) = createSong(id = id, name = name, albumArtist = "Ines Varga", album = "Symphonies", track = track, duration = 420_000).copy(
        composer = "Ludwig Harrow",
        composerSort = "Harrow, Ludwig",
        conductor = "Ines Varga",
        work = work,
        genres = listOf("Classical"),
    )
    val fifth = listOf("Allegro con brio", "Andante", "Scherzo", "Finale").mapIndexed { i, name -> movement(i + 1L, "Symphony No. 5 in C minor, Op. 67", "${listOf("I", "II", "III", "IV")[i]}. $name", i + 1) }
    val ninth = (1..24).map { n -> movement(10L + n, "Symphony No. 9 in D minor, Op. 125", "Part $n", n) }
    return classicalState(fifth + ninth, currentSong = fifth[1])
}

/** A 24-part work, so its last movements carry wide numerals ("XVIII", "XXIV"). */
fun longNumeralAlbumDetail(): AlbumDetailUiState {
    fun movement(id: Long, work: String, name: String, number: Int) = createSong(id = id, name = name, albumArtist = "Mira Calder", album = "Variations", track = number, duration = 120_000).copy(
        composer = "Ludwig Harrow",
        composerSort = "Harrow, Ludwig",
        work = work,
        movementName = name,
        movementNumber = number,
        genres = listOf("Classical"),
    )
    val prelude = listOf("Allegro", "Adagio").mapIndexed { i, name -> movement(i + 1L, "Sonata No. 3", name, i + 1) }
    val names = listOf("Andante", "Allegro", "Scherzo", "Minuet", "Largo", "Presto", "Waltz", "Gigue")
    val numerals = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI", "XXII", "XXIII", "XXIV")
    val variations = (1..24).map { n -> movement(10L + n, "Theme and Variations", "${numerals[n - 1]}. ${if (n == 24) "Finale" else names[n % names.size]}", n).copy(movementTotal = 12) }
    // A tagged total of 12 keeps the numerals Roman although the work runs past it; over 20 movements it starts collapsed, so expand it.
    return classicalState(prelude + variations, toggled = setOf(variations.first().id))
}

/** One long work split across two discs, as a double album side break does. */
fun splitWorkAlbumDetail(): AlbumDetailUiState {
    val songs = (1..6).map { n ->
        createSong(id = n.toLong(), name = "Part $n", albumArtist = "Mira Calder", album = "Heart Suite", track = if (n <= 3) n else n - 3, disc = if (n <= 3) 1 else 2, duration = 480_000).copy(
            composer = "Mira Calder",
            work = "Heart Suite",
            genres = listOf("Classical"),
        )
    }
    return classicalState(songs)
}

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
