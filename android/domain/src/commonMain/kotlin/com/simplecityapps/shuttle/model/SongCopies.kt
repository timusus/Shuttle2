package com.simplecityapps.shuttle.model

/** What decides whether a copy can play right now. The default, everything reachable and online, ranks on quality alone. */
data class SongCopyAvailability(
    val offline: Boolean = false,
    /** Paths of the server songs downloaded to the device. */
    val downloadedPaths: Set<String> = emptySet(),
    val unreachableProviders: Set<MediaProviderType> = emptySet(),
    /** Songs whose file has gone since the last scan. */
    val missingSongIds: Set<Long> = emptySet()
) {
    fun isOnDevice(song: Song): Boolean = !song.mediaProvider.remote || song.path in downloadedPaths

    fun isPlayable(song: Song): Boolean = song.id !in missingSongIds &&
        (isOnDevice(song) || (!offline && song.mediaProvider !in unreachableProviders))

    /**
     * Best copy first: one that plays now, then [preference] (a user's chosen source, say), then one on the device, then
     * lossless, then the higher bitrate, then the source's order; the id keeps the order total.
     */
    fun ranking(preference: Comparator<Song>? = null): Comparator<Song> = compareByDescending<Song> { isPlayable(it) }
        .then(preference ?: Comparator { _, _ -> 0 })
        .thenByDescending { isOnDevice(it) }
        .thenByDescending { it.isLossless }
        .thenByDescending { it.bitRate ?: 0 }
        .thenBy { it.mediaProvider.ordinal }
        .thenBy { it.id }
}

/** The codec when the source names one, otherwise the container's ("audio/flac", "audio/x-wav"). */
private val Song.isLossless: Boolean get() = isLosslessCodec(audioCodec ?: mimeType.substringAfter('/').removePrefix("x-"))

/**
 * Finds the copies of one song in different sources: songs of one album with the same MusicBrainz recording, or that
 * [matcher] pairs. A group never holds two songs of one source, so a source's own duplicates stay apart, as they do today.
 * [useRecordingIds] off leaves the plain-tag rule alone, to check it against a library that has the ids.
 */
class SongCopyGrouping(
    private val matcher: SongCopyMatcher = SongCopyMatcher(),
    private val useRecordingIds: Boolean = true
) {
    /** Each song's copies, the groups in the order of their first song and each group's songs in [songs]' order. */
    fun group(songs: List<Song>): List<List<Song>> {
        val groups = DisjointSources(songs)
        if (useRecordingIds) {
            songs.indices
                .filter { !songs[it].mbTrackId.isNullOrBlank() }
                .groupBy { songs[it].albumGroupKey to songs[it].mbTrackId!!.trim().lowercase() }
                .values.forEach { bucket -> bucket.forEach { groups.union(bucket.first(), it) } }
        }
        songs.indices
            .mapNotNull { index -> blockingHash(songs[index])?.let { hash -> (songs[index].albumGroupKey to hash) to index } }
            .groupBy({ it.first }, { it.second })
            .values
            .filter { bucket -> bucket.distinctBy { songs[it].mediaProvider }.size > 1 }
            .forEach { bucket ->
                val prepared = bucket.associateWith { matcher.prepare(songs[it].copyTags) }
                bucket.forEachIndexed { i, a ->
                    bucket.drop(i + 1).forEach { b ->
                        if (songs[a].mediaProvider != songs[b].mediaProvider && matcher.match(prepared.getValue(a), prepared.getValue(b)).isCopy) groups.union(a, b)
                    }
                }
            }
        return songs.indices.groupBy(groups::find).values.map { group -> group.map(songs::get) }
    }

    /**
     * One song per group, in the order of each group's first song: a lone song as it is, otherwise the best copy by
     * [ranking] carrying the group's history and its [Song.copies], best first. Each copy keeps its own row, so the
     * history folded here splits back into each copy's own once copies stop being merged.
     */
    fun merge(
        songs: List<Song>,
        ranking: Comparator<Song> = SongCopyAvailability().ranking()
    ): List<Song> = group(songs).map { group ->
        if (group.size == 1) return@map group.single()
        val copies = group.sortedWith(ranking)
        copies.first().copy(
            playCount = copies.sumOf { it.playCount },
            lastPlayed = copies.mapNotNull { it.lastPlayed }.maxOrNull(),
            lastCompleted = copies.mapNotNull { it.lastCompleted }.maxOrNull(),
            playbackPosition = copies.filter { it.lastPlayed != null }.maxByOrNull { it.lastPlayed!! }?.playbackPosition ?: copies.first().playbackPosition,
            favouritedAt = copies.mapNotNull { it.favouritedAt }.minOrNull(),
            dateAdded = copies.mapNotNull { it.dateAdded }.minOrNull(),
            copies = copies
        )
    }

    private fun blockingHash(song: Song): Long? = song.copyBlockingHash ?: matcher.blockingHash(song.album, song.disc, song.track)
}

/** Union-find over songs that joins two sets only when no source has a song in both. */
private class DisjointSources(private val songs: List<Song>) {
    private val parent = IntArray(songs.size) { it }
    private val sources = Array(songs.size) { setOf(songs[it].mediaProvider) }

    fun find(index: Int): Int {
        var root = index
        while (parent[root] != root) root = parent[root]
        parent[index] = root
        return root
    }

    fun union(
        a: Int,
        b: Int
    ) {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA == rootB || sources[rootA].any { it in sources[rootB] }) return
        val (root, child) = if (rootA < rootB) rootA to rootB else rootB to rootA
        parent[child] = root
        sources[root] = sources[rootA] + sources[rootB]
    }
}
