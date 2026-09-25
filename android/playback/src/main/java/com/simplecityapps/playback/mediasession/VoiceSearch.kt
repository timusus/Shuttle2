package com.simplecityapps.playback.mediasession

import android.os.Bundle
import android.provider.MediaStore
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import java.text.Normalizer
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * A voice search, as Assistant ("play Radiohead on S2") or Android Auto asks for one: the words [query] as spoken, and,
 * when the assistant has parsed them, a [focus] saying what kind of thing is asked for, with its parts ([artist],
 * [album], [title], [genre], [playlist]).
 */
data class VoiceSearch(
    val query: String?,
    val focus: Focus = Focus.Unstructured,
    val artist: String? = null,
    val album: String? = null,
    val title: String? = null,
    val genre: String? = null,
    val playlist: String? = null
) {
    enum class Focus {
        /** Anything that matches: no focus, or the "any" focus with words to match. */
        Unstructured,
        Artist,
        Album,
        Song,
        Genre,
        Playlist
    }

    /** "Play music": no words, and no part named. */
    val isBlank: Boolean
        get() = listOf(query, artist, album, title, genre, playlist).all { it.isNullOrBlank() }

    companion object {
        /** The search [query] and the intent or request [extras] describe, with the focus and parts [MediaStore] defines. */
        fun from(query: String?, extras: Bundle?): VoiceSearch = VoiceSearch(
            query = query,
            focus = focusOf(extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)),
            artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST),
            album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM),
            title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE),
            genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE),
            playlist = extras?.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)
        )

        // The item types are the focuses Android documents; the directory types are accepted as the same focus.
        private fun focusOf(focus: String?): Focus = when (focus) {
            MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE, MediaStore.Audio.Artists.CONTENT_TYPE -> Focus.Artist
            MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE, MediaStore.Audio.Albums.CONTENT_TYPE -> Focus.Album
            MediaStore.Audio.Media.ENTRY_CONTENT_TYPE, MediaStore.Audio.Media.CONTENT_TYPE -> Focus.Song
            MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE, MediaStore.Audio.Genres.CONTENT_TYPE -> Focus.Genre
            MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE, MediaStore.Audio.Playlists.CONTENT_TYPE -> Focus.Playlist
            else -> Focus.Unstructured
        }
    }
}

/** What a [VoiceSearch] resolves to. */
sealed interface VoiceSearchResult {
    /** Play [songs] from [position]. */
    data class Songs(val songs: List<Song>, val position: Int) : VoiceSearchResult

    /** A search for nothing in particular ([VoiceSearch.isBlank]): play whatever there is. */
    data object Anything : VoiceSearchResult

    /** Nothing to play: the library is empty. */
    data object Empty : VoiceSearchResult
}

/**
 * Resolves a [VoiceSearch] to the songs it asks for, by the closest match in the library: an artist's songs, an
 * album's, a song (with the rest of its album after it), a genre's or a playlist's. A focused search matches that kind
 * of thing, by the part the assistant parsed out, or by the words if it didn't; an unstructured one matches every kind
 * and plays the best. Nothing has to match exactly: a search that matches nothing well still plays the closest thing,
 * as a misheard name should.
 */
class VoiceSearchResolver
@Inject
constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository
) {
    suspend fun resolve(search: VoiceSearch): VoiceSearchResult {
        if (search.isBlank) return VoiceSearchResult.Anything
        val (songs, playlists) = withContext(Dispatchers.IO) {
            songRepository.getSongs(SongQuery.All()).firstOrNull().orEmpty() to
                playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)).firstOrNull().orEmpty()
        }
        val library = Library(songs, playlists)
        val focused = when (search.focus) {
            VoiceSearch.Focus.Unstructured -> emptyList()
            VoiceSearch.Focus.Artist -> library.artists(search.artist ?: search.query)
            VoiceSearch.Focus.Album -> library.albums(search.album ?: search.query, artistHint = search.artist)
            VoiceSearch.Focus.Song -> library.songs(search.title ?: search.query, artistHint = search.artist)
            VoiceSearch.Focus.Genre -> library.genres(search.genre ?: search.query)
            VoiceSearch.Focus.Playlist -> library.playlists(search.playlist ?: search.query)
        }
        // A focus with nothing of its kind in the library (no playlists, say) is searched as if unstructured.
        val candidates = focused.ifEmpty { library.everything(search.query ?: listOfNotNull(search.title, search.album, search.artist, search.genre, search.playlist).joinToString(" ")) }
        val best = candidates.maxByOrNull { it.score } ?: return VoiceSearchResult.Empty
        return best.songs()
    }

    /** A thing in the library a search might mean, how well it matches, and the songs to play for it. */
    private class Candidate(val score: Double, val songs: suspend () -> VoiceSearchResult.Songs)

    private inner class Library(val songs: List<Song>, val playlists: List<Playlist>) {
        fun artists(query: String?, bonus: Double = 0.0): List<Candidate> {
            val key = query?.searchKey().orEmpty()
            return songs
                .flatMap { song -> song.artistNames().map { name -> name.searchKey() to song } }
                .groupBy({ (name, _) -> name }, { (_, song) -> song })
                .filterKeys { name -> name.isNotEmpty() }
                .map { (name, songs) -> Candidate(matchScore(key, name) + bonus) { VoiceSearchResult.Songs(songs.distinct(), 0) } }
        }

        fun albums(query: String?, artistHint: String? = null, bonus: Double = 0.0): List<Candidate> {
            val key = query?.searchKey().orEmpty()
            return songs
                .filter { song -> !song.album.isNullOrBlank() }
                .groupBy { song -> song.albumGroupKey }
                .values
                .map { albumSongs ->
                    val song = albumSongs.first()
                    Candidate(nameScore(key, song.album!!, albumSongs.flatMap { it.artistNames() }.distinct(), artistHint) + bonus) {
                        VoiceSearchResult.Songs(albumSongs, 0)
                    }
                }
        }

        /** Each song, to play with the rest of its album after it, or on its own if it isn't on one. */
        fun songs(query: String?, artistHint: String? = null, bonus: Double = 0.0): List<Candidate> {
            val key = query?.searchKey().orEmpty()
            val albums by lazy { songs.filter { song -> !song.album.isNullOrBlank() }.groupBy { song -> song.albumGroupKey } }
            return songs
                .filter { song -> !song.name.isNullOrBlank() }
                .map { song ->
                    Candidate(nameScore(key, song.name!!, song.artistNames(), artistHint) + bonus) {
                        val album = if (song.album.isNullOrBlank()) null else albums[song.albumGroupKey]
                        if (album == null) VoiceSearchResult.Songs(listOf(song), 0) else VoiceSearchResult.Songs(album, album.indexOf(song).coerceAtLeast(0))
                    }
                }
        }

        fun genres(query: String?, bonus: Double = 0.0): List<Candidate> {
            val key = query?.searchKey().orEmpty()
            return songs
                .flatMap { song -> song.genres.map { genre -> genre.searchKey() to song } }
                .groupBy({ (genre, _) -> genre }, { (_, song) -> song })
                .filterKeys { genre -> genre.isNotEmpty() }
                .map { (genre, songs) -> Candidate(matchScore(key, genre) + bonus) { VoiceSearchResult.Songs(songs.distinct(), 0) } }
        }

        fun playlists(query: String?, bonus: Double = 0.0): List<Candidate> {
            val key = query?.searchKey().orEmpty()
            return playlists.map { playlist ->
                Candidate(matchScore(key, playlist.name.searchKey()) + bonus) {
                    val songs = withContext(Dispatchers.IO) { playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song } }
                    VoiceSearchResult.Songs(songs, 0)
                }
            }
        }

        /** Every kind of thing; where two match as well, an artist wins over an album, then a playlist, a song, a genre. */
        fun everything(query: String?): List<Candidate> = artists(query, bonus = 0.004) +
            albums(query, bonus = 0.003) +
            playlists(query, bonus = 0.002) +
            songs(query, bonus = 0.001) +
            genres(query)
    }
}

/** The names a song's artist goes by: its album artist and each of its artists. */
private fun Song.artistNames(): List<String> = (listOfNotNull(albumArtist) + artists).filter { it.isNotBlank() }.distinct()

/**
 * How well the search key [key] matches something called [name] by one of [artists]. The key may be the name alone
 * ("Creep") or the name with an artist ("Creep by Radiohead", "Radiohead Creep"); an [artistHint] the assistant parsed
 * out counts for a quarter of the score.
 */
private fun nameScore(
    key: String,
    name: String,
    artists: List<String>,
    artistHint: String?
): Double {
    val nameKey = name.searchKey()
    val artistKeys = artists.map { it.searchKey() }.filter { it.isNotEmpty() }
    if (!artistHint.isNullOrBlank()) {
        val hint = artistHint.searchKey()
        return 0.75 * matchScore(key, nameKey) + 0.25 * (artistKeys.maxOfOrNull { matchScore(hint, it) } ?: 0.0)
    }
    val withoutBy = key.split(' ').filter { it != "by" }.joinToString(" ")
    return artistKeys.fold(matchScore(key, nameKey)) { best, artist ->
        max(best, max(matchScore(withoutBy, "$nameKey $artist"), matchScore(withoutBy, "$artist $nameKey")))
    }
}

/**
 * How well two search keys match, from 0 to 1: 1 when they're the same; above 0.8 when one is the other with words
 * added ("OK Computer" and "OK Computer OKNOTOK"), the closer in length the higher; otherwise at most 0.8, by how alike
 * they're spelled (Jaro-Winkler), so a misheard or misspelt name still finds the closest.
 */
internal fun matchScore(key: String, candidate: String): Double {
    if (key.isEmpty() || candidate.isEmpty()) return 0.0
    if (key == candidate) return 1.0
    val (shorter, longer) = if (key.length <= candidate.length) key to candidate else candidate to key
    if (" $longer ".contains(" $shorter ")) return 0.8 + 0.15 * shorter.length / longer.length
    return 0.8 * jaroWinkler(key, candidate)
}

/**
 * [this] as a search key: lower case, without accents, apostrophes or punctuation, "&" as "and", single spaced, and
 * without a leading "the", "a" or "an".
 */
internal fun String.searchKey(): String {
    val words = Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace("&", " and ")
        .replace(apostrophes, "")
        .replace(nonWordCharacters, " ")
        .split(' ')
        .filter { it.isNotEmpty() }
    return (if (words.size > 1 && words.first() in articles) words.drop(1) else words).joinToString(" ")
}

private val combiningMarks = Regex("\\p{Mn}+")
private val apostrophes = Regex("['’`]")
private val nonWordCharacters = Regex("[^\\p{L}\\p{N}]+")
private val articles = setOf("the", "a", "an")

/** The Jaro-Winkler similarity of [a] and [b], from 0 (nothing alike) to 1 (the same). */
internal fun jaroWinkler(a: String, b: String): Double {
    if (a == b) return 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val window = max(0, max(a.length, b.length) / 2 - 1)
    val aMatched = BooleanArray(a.length)
    val bMatched = BooleanArray(b.length)
    var matches = 0
    for (i in a.indices) {
        for (j in max(0, i - window)..min(b.length - 1, i + window)) {
            if (!bMatched[j] && a[i] == b[j]) {
                aMatched[i] = true
                bMatched[j] = true
                matches++
                break
            }
        }
    }
    if (matches == 0) return 0.0
    var transpositions = 0
    var j = 0
    for (i in a.indices) {
        if (!aMatched[i]) continue
        while (!bMatched[j]) j++
        if (a[i] != b[j]) transpositions++
        j++
    }
    val m = matches.toDouble()
    val jaro = (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3
    val prefix = a.zip(b).take(4).takeWhile { (x, y) -> x == y }.size
    return jaro + prefix * 0.1 * (1 - jaro)
}
