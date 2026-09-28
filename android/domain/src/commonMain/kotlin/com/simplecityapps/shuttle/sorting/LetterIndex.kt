package com.simplecityapps.shuttle.sorting

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song

/** A run of items whose sort key starts with [letter], beginning at item [firstIndex]. */
data class LetterSection(val letter: String, val firstIndex: Int)

/** The section for keys that don't start with a letter of a short alphabet: digits, symbols, blanks, ideographs. */
const val OTHER_LETTER = "#"

/**
 * The letter [key] files under: its first non-blank character, uppercased with any accent dropped (É under E), or "#"
 * for anything that isn't a Latin, Greek or Cyrillic letter. Android's fast scroller and iOS's section index both
 * label their sections with it.
 */
fun letterLabel(key: String?): String {
    val first = key?.firstOrNull { !it.isWhitespace() } ?: return OTHER_LETTER
    if (!first.isLetter() || !first.isInShortAlphabet()) return OTHER_LETTER
    val base = if (first.code < 128) first else collatedWith[first] ?: baseLetter(first)
    return base.uppercaseChar().toString()
}

/**
 * Ligatures that don't decompose to a base letter but that every platform's collation expands (Æ as "ae", ß as "ss"),
 * so their items sit inside that letter's run. Letters like Ø and Ł sort with O and L under ICU but after Z under the
 * JDK's rules, so they keep their own entry.
 */
private val collatedWith = mapOf('Æ' to 'A', 'æ' to 'a', 'Œ' to 'O', 'œ' to 'o', 'ß' to 's')

/**
 * [items]' letter sections in list order: a new section wherever the letter of [key] changes. The list is already
 * sorted, so this is one pass; a key the sort compares keeps each letter's items together.
 */
fun <T> letterSections(items: List<T>, key: (T) -> String?): List<LetterSection> {
    val sections = mutableListOf<LetterSection>()
    items.forEachIndexed { index, item ->
        val letter = letterLabel(key(item))
        if (sections.lastOrNull()?.letter != letter) sections += LetterSection(letter, index)
    }
    return sections
}

/**
 * The name the songs list indexes by first letter under [sortOrder], the same one the sort compares; null for a sort
 * that isn't by name, which has no letter index.
 */
fun songLetterKey(sortOrder: SongSortOrder): ((Song) -> String?)? = when (sortOrder) {
    SongSortOrder.SongName -> { song -> song.name }

    SongSortOrder.ArtistGroupKey -> { song -> song.albumArtistGroupKey.key }

    SongSortOrder.AlbumGroupKey, SongSortOrder.Default -> { song -> song.albumGroupKey.key }

    SongSortOrder.Year, SongSortOrder.Duration, SongSortOrder.Track, SongSortOrder.PlayCount, SongSortOrder.LastModified,
    SongSortOrder.DateAdded, SongSortOrder.LastCompleted, SongSortOrder.Favourited,
    -> null
}

/** The name the albums list indexes by first letter under [sortOrder]; null for a sort that isn't by name. */
fun albumLetterKey(sortOrder: AlbumSortOrder): ((Album) -> String?)? = when (sortOrder) {
    AlbumSortOrder.AlbumName, AlbumSortOrder.Default -> { album -> album.groupKey?.key }
    AlbumSortOrder.ArtistGroupKey -> { album -> album.groupKey?.albumArtistGroupKey?.key }
    AlbumSortOrder.Year, AlbumSortOrder.PlayCount, AlbumSortOrder.RecentlyPlayed, AlbumSortOrder.Random -> null
}

/** The name the genres list indexes by first letter under [sortOrder]; null for a sort that isn't by name. */
fun genreLetterKey(sortOrder: GenreSortOrder): ((Genre) -> String?)? = when (sortOrder) {
    GenreSortOrder.Default -> { genre -> genre.name }
    GenreSortOrder.SongCount -> null
}

/**
 * The name the album artists list indexes by first letter. Both [AlbumArtistSortOrder]s compare it first (play count
 * only breaks ties), so an artists list always has its index.
 */
fun albumArtistLetterKey(albumArtist: AlbumArtist): String? = albumArtist.groupKey.key

// Each list's index, for [songs] (and so on) already in [sortOrder]; null for a sort that isn't by name. One pass over
// the list: the list screens compute it when the list or its sort changes, not on every state they publish.

fun songLetterIndex(songs: List<Song>, sortOrder: SongSortOrder): List<LetterSection>? = songLetterKey(sortOrder)?.let { letterSections(songs, it) }

fun albumLetterIndex(albums: List<Album>, sortOrder: AlbumSortOrder): List<LetterSection>? = albumLetterKey(sortOrder)?.let { letterSections(albums, it) }

fun genreLetterIndex(genres: List<Genre>, sortOrder: GenreSortOrder): List<LetterSection>? = genreLetterKey(sortOrder)?.let { letterSections(genres, it) }

fun albumArtistLetterIndex(albumArtists: List<AlbumArtist>): List<LetterSection> = letterSections(albumArtists, ::albumArtistLetterKey)

/** [letter] with its accent or other diacritic dropped: the first character of its canonical decomposition. */
internal expect fun baseLetter(letter: Char): Char

/** Latin, Greek and Cyrillic: scripts whose letters are few enough to index one by one. */
private fun Char.isInShortAlphabet(): Boolean = shortAlphabets.any { code in it }

// isLetter() has already dropped the symbols and digits these blocks also hold.
private val shortAlphabets = listOf(
    0x0041..0x02AF, // Basic Latin through Latin Extended-B, IPA extensions
    0x0370..0x03FF, // Greek and Coptic
    0x0400..0x052F, // Cyrillic and its supplement
    0x1C80..0x1C8F, // Cyrillic Extended-C
    0x1D00..0x1D7F, // Phonetic extensions
    0x1E00..0x1FFF, // Latin Extended Additional, Greek Extended
    0x2C60..0x2C7F, // Latin Extended-C
    0x2DE0..0x2DFF, // Cyrillic Extended-A
    0xA640..0xA69F, // Cyrillic Extended-B
    0xA720..0xA7FF, // Latin Extended-D
    0xAB30..0xAB6F, // Latin Extended-E
    0xFF21..0xFF5A, // Fullwidth Latin letters
)
