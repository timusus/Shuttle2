package com.simplecityapps.mediaprovider

import android.net.Uri
import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.Song

/**
 * Matches m3u [Entry] locations back to library [Song]s by file name and trailing path segments
 * rather than full path equality, since a device path recorded in the file may not round-trip
 * exactly (drive letters, SAF vs raw paths, or the file having moved since import). Shared by
 * import (`TaglibMediaProvider.findPlaylists`) and sync (`LocalPlaylistRepository.syncM3uFile`) so
 * both resolve the same entry the same way.
 *
 * An entry matches the song with the same file name (case-insensitive) that shares the most
 * trailing path segments with it; an exact full-path match always wins. If several songs tie for
 * the longest suffix the entry is ambiguous and matches nothing, rather than guessing.
 */
object M3uEntryMatcher {
    class Index internal constructor(internal val candidatesByFilename: Map<String, List<Candidate>>)

    internal class Candidate(
        val song: Song,
        val segments: List<String>
    )

    fun sanitisedPathsByFilename(songs: List<Song>): Index = Index(
        songs.map { Candidate(it, segments(Uri.decode(it.path))) }
            .filter { it.segments.isNotEmpty() }
            .groupBy { it.segments.last() }
    )

    fun match(
        entry: Entry,
        index: Index
    ): Song? {
        val entrySegments = segments(entry.location)
        val candidates = entrySegments.lastOrNull()?.let { index.candidatesByFilename[it] } ?: return null

        candidates.firstOrNull { it.segments == entrySegments }?.let { return it.song }

        val scored = candidates.map { it to commonSuffixLength(it.segments, entrySegments) }
        val best = scored.maxOf { it.second }
        return scored.singleOrNull { it.second == best }?.first?.song
    }

    /** Lower-cased path segments with separators unified, `.`/`..` resolved and any `drive:` or SAF `volume:` prefix removed from each. */
    private fun segments(path: String): List<String> {
        val result = ArrayList<String>()
        path.replace('\\', '/').split('/').forEach { raw ->
            when (val segment = raw.substringAfterLast(':').lowercase()) {
                "", "." -> Unit
                ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex)
                else -> result.add(segment)
            }
        }
        return result
    }

    private fun commonSuffixLength(
        a: List<String>,
        b: List<String>
    ): Int {
        var count = 0
        while (count < a.size && count < b.size && a[a.lastIndex - count] == b[b.lastIndex - count]) count++
        return count
    }
}
