package com.simplecityapps

import com.simplecityapps.shuttle.fixtures.SampleAlbum
import com.simplecityapps.shuttle.fixtures.SampleArtist
import com.simplecityapps.shuttle.fixtures.SampleGenre
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.fixtures.SamplePlaylist
import com.simplecityapps.shuttle.fixtures.SampleSong
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import kotlinx.datetime.LocalDate

// The sample library (`:android:fixtures`) as the app's models, for tests that want realistic
// names. With `SampleArtworkGlide` installed, these load the matching generated covers.

fun SampleSong.toSong(): Song = createSong(
    id = id,
    name = title,
    albumArtist = albumArtist,
    album = album,
    track = track,
    duration = durationSeconds * 1000,
    date = LocalDate(year, 1, 1),
    path = "/storage/emulated/0/Music/$albumArtist/$album/%02d $title.mp3".format(track),
).copy(artists = listOf(artist), genres = listOf(genre))

fun SampleAlbum.toAlbum(): Album = createAlbum(
    name = title,
    albumArtist = artist,
    artists = songs.map { it.artist }.distinct(),
    songCount = songs.size,
    duration = durationSeconds * 1000,
    year = year,
)

fun SampleArtist.toAlbumArtist(): AlbumArtist = createAlbumArtist(name = name, albumCount = albums.size, songCount = songCount)

fun SampleGenre.toGenre(): Genre = createGenre(name = name, songCount = songs.size, duration = songs.sumOf { it.durationSeconds } * 1000)

fun SamplePlaylist.toPlaylist(id: Long = 1): Playlist = createPlaylist(id = id, name = name, songCount = songs.size, duration = durationSeconds * 1000)

/** [SampleLibrary.queue] as songs: one from each album in turn. */
fun sampleSongs(size: Int = 8): List<Song> = SampleLibrary.queue(size).map { it.toSong() }
