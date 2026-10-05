package com.simplecityapps.shuttle.model

/**
 * Moves an album or album artist key across a change to the stored songs' tags, from the library [before] it to the one
 * [after] it, matching the songs by id: a key [after] names is kept; one only [before] names moves to the album (or album
 * artist) most of its songs belong to in [after]. Null for a key neither names, which a caller keeps as it is.
 *
 * For a change made to the stored songs directly rather than by reading their files again, so both libraries are at hand:
 * the local artist split of #880, applied to the songs stored before it.
 */
class AlbumKeyChange(
    private val before: AlbumIndex,
    private val after: AlbumIndex
) {
    /** The album [key] names after the change: itself when [after] has it, else the album its songs moved to. */
    fun album(key: AlbumGroupKey): AlbumGroupKey? = key.takeIf { after.songIds(it).isNotEmpty() }
        ?: before.songIds(key).mapNotNull { songId -> after.identities[songId]?.groupKey }.mostCommon()

    /** The album artist [key] names after the change; see [album]. */
    fun albumArtist(key: AlbumArtistGroupKey): AlbumArtistGroupKey? = key.takeIf { after.songIds(it).isNotEmpty() }
        ?: before.songIds(key).mapNotNull { songId -> after.identities[songId]?.albumArtistGroupKey }.mostCommon()

    /** [context] with its album or album artist moved; null when it names one neither library has. */
    fun context(context: PlayContext): PlayContext? = when (context) {
        is PlayContext.Album -> album(context.groupKey)?.let { PlayContext.Album(it) }
        is PlayContext.AlbumArtist -> albumArtist(context.groupKey)?.let { PlayContext.AlbumArtist(it) }
        else -> context
    }

    private fun <K> List<K>.mostCommon(): K? = groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<K, Int>> { it.value }.thenBy { it.key.toString() })
        .firstOrNull()?.key
}
