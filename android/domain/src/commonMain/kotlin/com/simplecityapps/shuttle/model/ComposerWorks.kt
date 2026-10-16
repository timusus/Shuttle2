package com.simplecityapps.shuttle.model

import dev.zacsweers.metro.Inject

/** One work of a composer, with every album in the library that holds a recording of it. */
data class ComposerWork(
    val title: String,
    val catalogue: String?,
    val recordings: List<OtherRecording>,
    /** Unique within a composer's works, unlike [title] and [catalogue], which drop the trimmed variants that tell works apart. */
    val key: String = "$title|$catalogue",
)

data class ComposerWorks(
    val name: String,
    val works: List<ComposerWork>,
    /** The albums holding any of the composer's works. */
    val albums: List<AlbumGroupKey>,
)

/**
 * The works of one composer in the library. Only strictly classical songs count, a composer's spellings ("Mahler",
 * "Gustav Mahler") are one person, and recordings of a work are matched the way [FindOtherRecordings] matches them.
 * Null when the library holds none of the composer's works.
 */
@Inject
class FindComposerWorks {
    private class Cluster(val identity: WorkIdentity, val title: String, val songs: MutableList<Song> = mutableListOf())

    operator fun invoke(composer: String, library: List<Song>): ComposerWorks? {
        // Blocks are judged as the album page judges them, so a work that qualifies through its movement titles counts.
        val blocks = library.filter { sameComposer(it.composer, composer) }
            .map { it.albumGroupKey }.distinct().toSet()
            .let { keys -> library.filter { it.albumGroupKey in keys } }
            .groupBy { it.albumGroupKey }.values
            .flatMap { album -> workBlocksOf(composer, album.sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: 0 }))) }
        if (blocks.isEmpty()) return null
        val songs = blocks.flatMap { it.second }

        val clusters = mutableListOf<Cluster>()
        for ((work, blockSongs) in blocks) {
            val cluster = clusters.firstOrNull { it.identity.matches(work) } ?: Cluster(WorkIdentity(work), work).also(clusters::add)
            cluster.songs += blockSongs
        }
        val works = clusters.map { cluster ->
            val parsed = parseWorkTitle(cluster.title)
            val recordings = cluster.songs.groupBy { it.albumGroupKey }
                .map { (key, albumSongs) ->
                    val ordered = albumSongs.sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: 0 }))
                    OtherRecording(key, ordered.first().album, ordered.first().albumArtist, ordered)
                }
                .sortedWith(compareByDescending<OtherRecording> { it.year ?: 0 }.thenBy { it.album.orEmpty().lowercase() })
            ComposerWork(parsed.core, parsed.catalogue, recordings, key = cluster.title)
        }.sortedBy { it.title.lowercase() }

        val named = songs.firstNotNullOf { it.composerName() }
        return ComposerWorks(named.fullName(), works, songs.map { it.albumGroupKey }.distinct())
    }

    /** The composer's classical works in one album, as (work title, songs): work blocks, and lone classical tracks the album page leaves as tracks. */
    private fun workBlocksOf(composer: String, album: List<Song>): List<Pair<String, List<Song>>> = groupWorks(album).mapNotNull { item ->
        when (item) {
            is AlbumItem.Work -> item.block.takeIf { sameComposer(it.composer, composer) && it.songs.isClassicalBlock() }?.let { it.work to it.songs }
            is AlbumItem.Track -> item.song.takeIf { sameComposer(it.composer, composer) && listOf(it).isClassicalBlock() }?.let { song -> song.workName()?.let { it to listOf(song) } }
        }
    }
}
