package com.simplecityapps.shuttle.model

/** Tells apart albums of the same name and album artist, by MusicBrainz release, server album id or folder. */
data class AlbumGroupKey(
    val key: String?,
    val albumArtistGroupKey: AlbumArtistGroupKey?,
    val identity: String? = null
) {
    /** Stored form; a key without an identity keeps the two-part form older rows use, which [decode] still reads. */
    fun encode(): String = if (identity == null) encodeParts(albumArtistGroupKey?.key, key) else encodeParts(albumArtistGroupKey?.key, key, identity)

    companion object {
        /** Null for a string that isn't a key; keys stored without an identity part read back as name-rule keys. */
        fun decode(encoded: String): AlbumGroupKey? = decodeParts(encoded).takeIf { it.size in 2..3 }?.let { parts ->
            AlbumGroupKey(parts[1], AlbumArtistGroupKey(parts[0]), parts.getOrNull(2))
        }

        // A key's parts can be null, so each is written as "" for null or "=" and its value, joined by a unit separator.
        private const val SEPARATOR = '\u001F'

        internal fun encodeParts(vararg parts: String?): String = parts.joinToString(SEPARATOR.toString()) { part -> part?.let { "=$it" }.orEmpty() }

        internal fun decodeParts(encoded: String): List<String?> = encoded.split(SEPARATOR).map { part -> if (part.startsWith("=")) part.substring(1) else null }
    }
}
