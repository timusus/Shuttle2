package com.simplecityapps.shuttle.model

/** What decides whether a copy can play right now. The default, everything reachable and online, ranks on quality alone. */
data class SongCopyAvailability(
    val offline: Boolean = false,
    /** Paths of the server songs downloaded to the device. */
    val downloadedPaths: Set<String> = emptySet(),
    val unreachableProviders: Set<MediaProviderType> = emptySet(),
    /** Songs whose file has gone since the last scan. */
    val missingSongIds: Set<Long> = emptySet(),
    /** The streaming quality's cap in kbps, or null for the original file: a stream above it plays transcoded down to it. */
    val streamingCapKbps: Int? = null
) {
    fun isOnDevice(song: Song): Boolean = !song.mediaProvider.remote || song.path in downloadedPaths

    /** A capped stream is a lossy transcode at the cap, so a copy ranks by what it plays as, not what its source holds. */
    private fun isStreamCapped(song: Song): Boolean = streamingCapKbps != null && !isOnDevice(song)

    private fun deliversLossless(song: Song): Boolean = song.isLossless && !isStreamCapped(song)

    private fun deliveredBitRate(song: Song): Int = (song.bitRate ?: 0).let { rate -> if (isStreamCapped(song)) minOf(rate, streamingCapKbps!!) else rate }

    /** The copy that plays at the higher quality first: lossless, then the higher bitrate as delivered. */
    val quality: Comparator<Song> = compareByDescending<Song> { deliversLossless(it) }.thenByDescending { deliveredBitRate(it) }

    fun isPlayable(song: Song): Boolean = song.id !in missingSongIds &&
        (isOnDevice(song) || (!offline && song.mediaProvider !in unreachableProviders))

    /**
     * Best copy first: one that plays now, then [preference] (a user's chosen source, say), then one on the device, then
     * lossless, then the higher bitrate, then the source's order; the id keeps the order total.
     */
    fun ranking(preference: Comparator<Song>? = null): Comparator<Song> = compareByDescending<Song> { isPlayable(it) }
        .then(preference ?: Comparator { _, _ -> 0 })
        .thenByDescending { isOnDevice(it) }
        .then(quality)
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
    /**
     * Each song's copies, the groups in the order of their first song and each group's songs in [songs]' order. Every
     * two songs of a group are linked themselves, so A matching B and B matching C never joins A and C on its own.
     */
    fun group(songs: List<Song>): List<List<Song>> {
        val links = CopyLinks(songs)
        if (useRecordingIds) {
            songs.indices
                .filter { !songs[it].mbTrackId.isNullOrBlank() }
                .groupBy { songs[it].albumGroupKey to songs[it].mbTrackId!!.trim().lowercase() }
                .values.forEach { bucket -> bucket.forEachIndexed { i, a -> bucket.drop(i + 1).forEach { b -> links.link(a, b, Double.POSITIVE_INFINITY) } } }
        }
        songs.indices
            .mapNotNull { index -> blockingHash(songs[index])?.let { hash -> (songs[index].albumGroupKey to hash) to index } }
            .groupBy({ it.first }, { it.second })
            .values
            .filter { bucket -> bucket.distinctBy { songs[it].mediaProvider }.size > 1 }
            .forEach { bucket ->
                val prepared = bucket.associateWith { matcher.prepare(songs[it].copyTags) }
                bucket.forEachIndexed { i, a ->
                    bucket.drop(i + 1).filter { b -> songs[a].mediaProvider != songs[b].mediaProvider }.forEach { b ->
                        val match = matcher.match(prepared.getValue(a), prepared.getValue(b))
                        if (match.isCopy) links.link(a, b, match.score)
                    }
                }
            }
        return links.groups().map { group -> group.map(songs::get) }
    }

    /** Each group of [songs] as [merged] lists it, in the order of each group's first song. */
    fun merge(
        songs: List<Song>,
        ranking: Comparator<Song> = SongCopyAvailability().ranking()
    ): List<Song> = group(songs).flatMap { group -> merged(group, ranking) }

    /**
     * One group's songs as a library lists them: its excluded copies apart, so excluding one copy leaves the others
     * showing, and the rest as one song, the best copy by [ranking] carrying their history and its [Song.copies], best
     * first. Each copy keeps its own row, so the history folded here splits back into each copy's own once copies stop
     * being merged.
     */
    fun merged(
        group: List<Song>,
        ranking: Comparator<Song>
    ): List<Song> {
        val (excluded, shown) = group.partition { it.blacklisted }
        if (shown.size <= 1) return shown + excluded
        val copies = shown.sortedWith(ranking)
        return listOf(
            copies.first().copy(
                playCount = copies.sumOf { it.playCount },
                lastPlayed = copies.mapNotNull { it.lastPlayed }.maxOrNull(),
                lastCompleted = copies.mapNotNull { it.lastCompleted }.maxOrNull(),
                playbackPosition = copies.filter { it.lastPlayed != null }.maxByOrNull { it.lastPlayed!! }?.playbackPosition ?: copies.first().playbackPosition,
                favouritedAt = copies.mapNotNull { it.favouritedAt }.minOrNull(),
                dateAdded = copies.mapNotNull { it.dateAdded }.minOrNull(),
                copies = copies
            )
        ) + excluded
    }

    private fun blockingHash(song: Song): Long? = song.copyBlockingHash ?: matcher.blockingHash(song.album, song.disc, song.track)
}

/**
 * Complete-linkage clustering of songs over pairwise copy links: strongest link first, two groups join only when every
 * song of one is linked to every song of the other and no source has a song in both.
 */
private class CopyLinks(private val songs: List<Song>) {
    private class Link(val a: Int, val b: Int, val strength: Double)

    private val links = ArrayList<Link>()
    private val linked = HashSet<Long>()

    fun link(
        a: Int,
        b: Int,
        strength: Double
    ) {
        if (songs[a].mediaProvider == songs[b].mediaProvider || !linked.add(key(a, b))) return
        links += Link(a, b, strength)
    }

    fun groups(): List<List<Int>> {
        val groupOf = IntArray(songs.size) { it }
        val members = Array(songs.size) { mutableListOf(it) }
        links.sortedByDescending { it.strength }.forEach { link ->
            val (into, from) = listOf(groupOf[link.a], groupOf[link.b]).sorted()
            if (into == from) return@forEach
            val joinable = members[into].all { a -> members[from].all { b -> songs[a].mediaProvider != songs[b].mediaProvider && key(a, b) in linked } }
            if (!joinable) return@forEach
            members[from].forEach { groupOf[it] = into }
            members[into] += members[from]
            members[from].clear()
        }
        return songs.indices.groupBy { groupOf[it] }.values.toList()
    }

    private fun key(
        a: Int,
        b: Int
    ): Long = minOf(a, b).toLong() * songs.size + maxOf(a, b)
}
