package com.simplecityapps.shuttle.fixtures

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.squareup.moshi.Moshi
import java.util.concurrent.ConcurrentHashMap

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
        @Suppress("UNCHECKED_CAST")
        val manifest = Moshi.Builder().build().adapter(Any::class.java).fromJson(resourceText("library.json")) as Map<String, Any?>
        albums = (manifest.getValue("albums") as List<Map<String, Any?>>).mapIndexed(::parseAlbum)
        val songsByRef = albums.flatMap { album -> album.songs.map { "${album.id}/${it.track}" to it } }.toMap()
        playlists = (manifest.getValue("playlists") as List<Map<String, Any?>>).map { playlist ->
            SamplePlaylist(
                name = playlist.getValue("name") as String,
                songs = (playlist.getValue("tracks") as List<String>).map { ref -> songsByRef[ref] ?: error("Playlist track $ref isn't in the library") },
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

    private fun parseAlbum(index: Int, json: Map<String, Any?>): SampleAlbum {
        val id = json.getValue("id") as String
        val title = json.getValue("title") as String
        val artist = json.getValue("artist") as String
        val year = (json.getValue("year") as Number).toInt()
        val genre = json.getValue("genre") as String

        @Suppress("UNCHECKED_CAST")
        val songs = (json.getValue("tracks") as List<Map<String, Any?>>).mapIndexed { trackIndex, track ->
            SampleSong(
                id = (index + 1) * 100L + trackIndex + 1,
                title = track.getValue("title") as String,
                artist = track["artist"] as String? ?: artist,
                albumArtist = artist,
                album = title,
                albumId = id,
                track = trackIndex + 1,
                durationSeconds = (track.getValue("duration") as Number).toInt(),
                year = year,
                genre = genre,
            )
        }
        return SampleAlbum(id, title, artist, year, genre, songs)
    }

    private fun resourceBytes(path: String): ByteArray = SampleLibrary::class.java.classLoader!!.getResourceAsStream("$ROOT/$path")
        ?.use { it.readBytes() }
        ?: error("Missing fixture resource $ROOT/$path; run support/scripts/generate-fake-artwork.py")

    private fun resourceText(path: String): String = resourceBytes(path).decodeToString()
}

internal fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
