package com.simplecityapps.shuttle.model

/** One source an album has songs in, for the album's "Primary source" picker. */
data class AlbumSource(
    val provider: MediaProviderType,
    /** How many of the album's songs this source holds a copy of. */
    val songCount: Int,
    val primary: Boolean,
)

/** The sources behind an album's songs: "10 of 12 songs" is [AlbumSource.songCount] of [songCount]. */
data class AlbumSources(
    val songCount: Int,
    val sources: List<AlbumSource>,
    /** Whether no source is primary, so each song's best copy stands. */
    val automatic: Boolean,
) {
    val sourceCount: Int get() = sources.size

    /** The album shows source choices only when it has songs in more than one source. */
    val hasChoice: Boolean get() = sources.size > 1
}

/** [albumSongs] are the album's merged songs; each song's [Song.copies] say which sources hold it. */
fun albumSources(albumSongs: List<Song>, primaryAlbums: Set<String> = emptySet()): AlbumSources {
    val holders = albumSongs.map { song -> (song.copies.ifEmpty { listOf(song) }).map { it.mediaProvider }.toSet() }
    val copies = albumSongs.flatMap { it.copies.ifEmpty { listOf(it) } }
    val sources = copies.map { it.mediaProvider }.distinct().map { provider ->
        AlbumSource(
            provider = provider,
            songCount = holders.count { provider in it },
            primary = copies.any { it.mediaProvider == provider && it.copyAlbumKey in primaryAlbums }
        )
    }.sortedWith(compareByDescending<AlbumSource> { it.songCount }.thenBy { it.provider.ordinal })
    return AlbumSources(albumSongs.size, sources, automatic = sources.none { it.primary })
}

/** How many sources hold a copy of this song; a song only lists several copies while the library merges them. */
val Song.sourceCount: Int get() = copies.map { it.mediaProvider }.distinct().size
