package com.simplecityapps.shuttle.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import com.simplecityapps.shuttle.designsystem.component.LocalPreviewArtwork
import com.simplecityapps.shuttle.designsystem.component.PreviewArtwork
import com.simplecityapps.shuttle.designsystem.preview.SampleCovers
import com.simplecityapps.shuttle.fixtures.SampleAlbum
import com.simplecityapps.shuttle.fixtures.SampleArtist
import com.simplecityapps.shuttle.fixtures.SampleGenre
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.fixtures.SamplePlaylist
import com.simplecityapps.shuttle.fixtures.SampleSong
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.removeArticles
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import kotlinx.datetime.LocalDate

// The sample library (:android:fixtures) as the app's models, for @Previews and unit tests that want realistic
// names: invented names, and covers drawn through LocalPreviewArtwork. Wrap a preview in
// `S2Preview(artwork = SampleAppCovers) { }`, or `SampleArtwork { }` inside a preview's own theme. The fixtures are
// debugImplementation plus releaseCompileOnly: this compiles in main source for the previews, Android Studio renders
// them from the debug variant, and release builds package none of it (R8 drops this file). `check` runs
// verifyFixturesNotInReleaseClasspath, which fails if any module's release runtime classpath reaches the fixtures;
// still, only call these from @Preview functions or test code, never from a code path a release build can execute.

/** Covers for the app's [Song]s, [Album]s and [AlbumArtist]s named after sample ones, and for the sample models themselves. */
object SampleAppCovers : PreviewArtwork {
    override fun image(model: Any): ImageBitmap? {
        val sample = when (model) {
            is Song -> model.album?.let(SampleLibrary::albumNamed)
            is Album -> model.name?.let(SampleLibrary::albumNamed)
            is AlbumArtist -> SampleLibrary.artists.firstOrNull { it.name == model.name }
            else -> model
        }
        return sample?.let(SampleCovers::image)
    }
}

/** Provides [SampleAppCovers] to [content], for previews that keep their own theme. */
@Composable
fun SampleArtwork(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPreviewArtwork provides SampleAppCovers, content = content)
}

fun SampleSong.toSong(): Song = Song(
    id = id,
    name = title,
    albumArtist = albumArtist,
    artists = listOf(artist),
    album = album,
    track = track,
    disc = 1,
    duration = durationSeconds * 1000,
    date = LocalDate(year, 1, 1),
    genres = listOf(genre),
    path = "/storage/emulated/0/Music/$albumArtist/$album/%02d $title.flac".format(track),
    size = 1,
    mimeType = "audio/flac",
    lastModified = null,
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    mediaProvider = MediaProviderType.Shuttle,
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null,
)

/** The album, grouped the way its [toSong]s are, so screens match songs to it as they do real data. */
fun SampleAlbum.toAlbum(): Album {
    val songs = songs.map { it.toSong() }
    return Album(
        name = title,
        albumArtist = artist,
        artists = songs.flatMap { it.artists }.distinct(),
        songCount = songs.size,
        duration = songs.sumOf { it.duration },
        year = year,
        playCount = 0,
        lastSongPlayed = null,
        lastSongCompleted = null,
        groupKey = songs.first().albumGroupKey,
        mediaProviders = listOf(MediaProviderType.Shuttle),
    )
}

fun SampleArtist.toAlbumArtist(): AlbumArtist = AlbumArtist(
    name = name,
    artists = listOf(name),
    albumCount = albums.size,
    songCount = songCount,
    playCount = 0,
    groupKey = AlbumArtistGroupKey(name.lowercase().removeArticles()),
    mediaProviders = listOf(MediaProviderType.Shuttle),
)

fun SampleGenre.toGenre(): Genre = Genre(
    name = name,
    songCount = songs.size,
    duration = songs.sumOf { it.durationSeconds } * 1000,
    mediaProviders = listOf(MediaProviderType.Shuttle),
)

fun SamplePlaylist.toPlaylist(id: Long): Playlist = Playlist(
    id = id,
    name = name,
    songCount = songs.size,
    duration = durationSeconds * 1000,
    sortOrder = PlaylistSongSortOrder.Position,
    mediaProvider = MediaProviderType.Shuttle,
    externalId = null,
)

/** Every sample playlist as a [Playlist], ids from 1. */
fun samplePlaylists(): List<Playlist> = SampleLibrary.playlists.mapIndexed { index, playlist -> playlist.toPlaylist(id = index + 1L) }

/** A queue of [size] sample-library songs (one from each album in turn), for previews and tests that want realistic names. */
fun sampleSongs(size: Int = 8): List<Song> = SampleLibrary.queue(size).map { it.toSong() }
