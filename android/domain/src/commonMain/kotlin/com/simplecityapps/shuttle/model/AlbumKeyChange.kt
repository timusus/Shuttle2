package com.simplecityapps.shuttle.model

/** Moves keys across a tag change made to stored songs directly, where both libraries are at hand. */
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
