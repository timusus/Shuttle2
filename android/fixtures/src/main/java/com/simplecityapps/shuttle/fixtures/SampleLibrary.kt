package com.simplecityapps.shuttle.fixtures

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A sample song. [id] is stable: the album's position in the manifest times 100, plus the track number. */
data class SampleSong(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val albumId: String,
    val track: Int,
    val durationSeconds: Int,
    val year: Int,
    val genre: String,
) {
    /** The duration as a row shows it, `m:ss`. */
    val duration: String get() = formatDuration(durationSeconds)
}

data class SampleAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int,
    val genre: String,
    val songs: List<SampleSong>,
) {
    val durationSeconds: Int get() = songs.sumOf { it.durationSeconds }
    val isCompilation: Boolean get() = artist == SampleLibrary.VARIOUS_ARTISTS
}

/** An album artist. Artist images use the cover of their first album ([coverAlbumId]). */
data class SampleArtist(
    val name: String,
    val albums: List<SampleAlbum>,
) {
    val songCount: Int get() = albums.sumOf { it.songs.size }
    val coverAlbumId: String get() = albums.first().id
}

data class SampleGenre(
    val name: String,
    val songs: List<SampleSong>,
)

data class SamplePlaylist(
    val name: String,
    val songs: List<SampleSong>,
) {
    val durationSeconds: Int get() = songs.sumOf { it.durationSeconds }
}

/**
 * The invented sample library: artists, albums, songs, genres and playlists read from
 * `sample-library/library.json`, and the covers `support/scripts/generate-fake-artwork.py` draws
 * from it. Every name is made up and every cover is generated, so screenshots can show realistic
 * content without real releases.
 */
object SampleLibrary {
    const val VARIOUS_ARTISTS = "Various Artists"
    private const val ROOT = "sample-library"

    val albums: List<SampleAlbum>
    val playlists: List<SamplePlaylist>

    init {
        val manifest = manifestJson.decodeFromString<Manifest>(resourceText("library.json"))
        albums = manifest.albums.mapIndexed(::parseAlbum)
        val songsByRef = albums.flatMap { album -> album.songs.map { "${album.id}/${it.track}" to it } }.toMap()
        playlists = manifest.playlists.map { playlist ->
            SamplePlaylist(
                name = playlist.name,
                songs = playlist.tracks.map { ref -> songsByRef[ref] ?: error("Playlist track $ref isn't in the library") },
            )
        }
    }

    val songs: List<SampleSong> = albums.flatMap { it.songs }

    /** Album artists in manifest order, the various-artists compilation excluded. */
    val artists: List<SampleArtist> = albums.filterNot { it.isCompilation }
        .groupBy { it.artist }
        .map { (name, albums) -> SampleArtist(name, albums) }

    val genres: List<SampleGenre> = songs.groupBy { it.genre }.map { (name, songs) -> SampleGenre(name, songs) }

    /** The album whose title runs long enough to test truncation. */
    val longTitleAlbum: SampleAlbum get() = albums.maxBy { it.title.length }

    val compilation: SampleAlbum get() = albums.first { it.isCompilation }

    fun album(id: String): SampleAlbum = albums.firstOrNull { it.id == id } ?: error("No sample album '$id'")

    /** The album called [title], or null. Matches how the app's models refer to an album: by name. */
    fun albumNamed(title: String): SampleAlbum? = albums.firstOrNull { it.title == title }

    fun artist(name: String): SampleArtist = artists.firstOrNull { it.name == name } ?: error("No sample artist '$name'")

    fun playlist(name: String): SamplePlaylist = playlists.firstOrNull { it.name == name } ?: error("No sample playlist '$name'")

    /** A queue of [size] songs, one from each album in turn, so neighbouring rows show different covers. */
    fun queue(size: Int = 8): List<SampleSong> {
        val tracks = albums.map { it.songs }
        return generateSequence(0) { it + 1 }
            .flatMap { round -> tracks.mapNotNull { it.getOrNull(round) } }
            .take(size.coerceAtMost(songs.size))
            .toList()
    }

    /** The JPEG bytes of [albumId]'s cover, 600x600. */
    fun coverBytes(albumId: String): ByteArray = resourceBytes("covers/$albumId.jpg")

    private val bitmaps = ConcurrentHashMap<String, ImageBitmap>()

    /** [albumId]'s cover decoded for Compose (`Image(cover(id), ...)`), cached. */
    fun cover(albumId: String): ImageBitmap = bitmaps.getOrPut(albumId) {
        val bytes = coverBytes(albumId)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
    }

    private fun parseAlbum(index: Int, album: ManifestAlbum): SampleAlbum {
        val songs = album.tracks.mapIndexed { trackIndex, track ->
            SampleSong(
                id = (index + 1) * 100L + trackIndex + 1,
                title = track.title,
                artist = track.artist ?: album.artist,
                albumArtist = album.artist,
                album = album.title,
                albumId = album.id,
                track = trackIndex + 1,
                durationSeconds = track.duration,
                year = album.year,
                genre = album.genre,
            )
        }
        return SampleAlbum(album.id, album.title, album.artist, album.year, album.genre, songs)
    }

    private fun resourceBytes(path: String): ByteArray = SampleLibrary::class.java.classLoader!!.getResourceAsStream("$ROOT/$path")
        ?.use { it.readBytes() }
        ?: error("Missing fixture resource $ROOT/$path; run support/scripts/generate-fake-artwork.py")

    private fun resourceText(path: String): String = resourceBytes(path).decodeToString()
}

internal fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private val manifestJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class Manifest(
    val albums: List<ManifestAlbum>,
    val playlists: List<ManifestPlaylist>,
)

@Serializable
private data class ManifestAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int,
    val genre: String,
    val tracks: List<ManifestTrack>,
)

@Serializable
private data class ManifestTrack(
    val title: String,
    val artist: String? = null,
    val duration: Int,
)

@Serializable
private data class ManifestPlaylist(
    val name: String,
    val tracks: List<String>,
)
