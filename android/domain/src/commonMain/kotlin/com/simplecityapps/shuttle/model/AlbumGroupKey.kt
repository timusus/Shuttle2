package com.simplecityapps.shuttle.model

/**
 * Which album a song belongs to, as [AlbumIdentityRule] decides it (#637): the album's name ([key], case-folded and
 * without articles, what albums sort and index by), its album artist, and [identity], what tells it apart from another
 * album of the same name and album artist.
 *
 * [identity] is `mb:<release id>` for an album keyed by its MusicBrainz id, `<source>:<album id>` (`jellyfin:…`) for one
 * keyed by its server's album id, `dir:<folder>` for an untagged album of several artists, told apart by its folder,
 * and null for one the name rule alone names.
 */
data class AlbumGroupKey(
    val key: String?,
    val albumArtistGroupKey: AlbumArtistGroupKey?,
    val identity: String? = null
) {
    /** The key as one string, for storing it; a name-rule key has no identity part, as before #637 (a play event's context, a pinned collection): [decode] reads it back. */
    fun encode(): String = if (identity == null) encodeParts(albumArtistGroupKey?.key, key) else encodeParts(albumArtistGroupKey?.key, key, identity)

    companion object {
        /**
         * The key [encoded] names; null for a string that isn't one. A key stored before #637 has no identity part, and
         * reads back as a name-rule key.
         */
        fun decode(encoded: String): AlbumGroupKey? = decodeParts(encoded).takeIf { it.size in 2..3 }?.let { parts ->
            AlbumGroupKey(parts[1], AlbumArtistGroupKey(parts[0]), parts.getOrNull(2))
        }

        // A key's parts can be null, so each is written as "" for null or "=" and its value, joined by a unit separator.
        private const val SEPARATOR = '\u001F'

        internal fun encodeParts(vararg parts: String?): String = parts.joinToString(SEPARATOR.toString()) { part -> part?.let { "=$it" }.orEmpty() }

        internal fun decodeParts(encoded: String): List<String?> = encoded.split(SEPARATOR).map { part -> if (part.startsWith("=")) part.substring(1) else null }
    }
}
