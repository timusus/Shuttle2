package com.simplecityapps.shuttle.model

import dev.zacsweers.metro.Inject

/** One work of a composer, with every album in the library that holds a recording of it. */
data class ComposerWork(
    val title: String,
    val catalogue: String?,
    val recordings: List<OtherRecording>,
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
        val songs = library.filter { song ->
            sameComposer(song.composer, composer) && listOf(song).isClassicalBlock() && song.workName() != null
        }
        if (songs.isEmpty()) return null

        val clusters = mutableListOf<Cluster>()
        for (song in songs) {
            val work = song.workName() ?: continue
            val cluster = clusters.firstOrNull { it.identity.matches(work) } ?: Cluster(WorkIdentity(work), work).also(clusters::add)
            cluster.songs += song
        }
        val works = clusters.map { cluster ->
            val parsed = parseWorkTitle(cluster.title)
            val recordings = cluster.songs.groupBy { it.albumGroupKey }
                .map { (key, albumSongs) ->
                    val ordered = albumSongs.sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: 0 }))
                    OtherRecording(key, ordered.first().album, ordered.first().albumArtist, ordered)
                }
                .sortedWith(compareByDescending<OtherRecording> { it.year ?: 0 }.thenBy { it.album.orEmpty().lowercase() })
            ComposerWork(parsed.core, parsed.catalogue, recordings)
        }.sortedBy { it.title.lowercase() }

        val named = songs.firstNotNullOf { it.composerName() }
        return ComposerWorks(named.fullName(), works, songs.map { it.albumGroupKey }.distinct())
    }
}
