package com.simplecityapps.shuttle.model

/** A credit carrying the album artist's own id is that artist however it's spelt, so a variant on their album isn't an appearance. */
object ArtistCredits {
    private val VARIOUS_ARTISTS_KEY = AlbumArtistGroupKey(AlbumIdentityRule.artistKey(AlbumIdentityRule.VARIOUS_ARTISTS))

    /** The artists [tags] credits, each once, in credit order; [identity] is the song's album identity. */
    fun credits(tags: AlbumIdentityTags, identity: AlbumIdentity): List<ArtistCredit> = creditsWithServerIds(tags, identity).map { (credit, _) -> credit }.distinctBy { it.groupKey }

    /** Null unless the song pins the artist down, so no server image is borrowed from a duet partner. */
    fun serverArtistId(tags: AlbumIdentityTags, identity: AlbumIdentity, key: AlbumArtistGroupKey): String? {
        if (key.key == null || key == VARIOUS_ARTISTS_KEY) return null
        val albumArtists = identity.albumArtistKeys
        if (key in albumArtists) {
            if (albumArtists.size == 1) return tags.serverAlbumArtistIds.single()
            return tags.serverAlbumArtistIds.pairedWith(albumArtists)?.get(albumArtists.indexOf(key))?.ifEmpty { null }
        }
        return creditsWithServerIds(tags, identity)
            .filter { (credit, _) -> credit.groupKey == key }
            .mapNotNull { (_, serverId) -> serverId?.takeIf { it.isNotEmpty() } }
            .distinct()
            .singleOrNull()
    }

    /** Each credit in order, before deduplication, with the server artist id paired with it (null when ids don't pair). */
    private fun creditsWithServerIds(tags: AlbumIdentityTags, identity: AlbumIdentity): List<Pair<ArtistCredit, String?>> {
        val multiValue = tags.artistsTag.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        val names = multiValue.ifEmpty { tags.artists.flatMap(ArtistNames::split) }
        // An id names the album artist only when the album has one
        val albumArtistKey = identity.albumArtistKeys.singleOrNull()
        val mbIds = tags.mbArtistIds.pairedWith(names)
        val serverIds = tags.serverArtistIds.pairedWith(names)
        val albumArtistMbId = tags.mbAlbumArtistIds.single()
        val albumArtistServerId = tags.serverAlbumArtistIds.single()
        val trackArtists = names.mapIndexed { index, name ->
            val isAlbumArtist = (albumArtistMbId != null && mbIds?.get(index)?.equals(albumArtistMbId, ignoreCase = true) == true) ||
                (albumArtistServerId != null && serverIds?.get(index) == albumArtistServerId)
            ArtistCredit(name, albumArtistKey?.takeIf { isAlbumArtist } ?: AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name))) to serverIds?.get(index)
        }
        return trackArtists + identity.featuredArtists.map { name -> ArtistCredit(name, AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name))) to null }
    }

    private fun List<String>?.pairedWith(names: List<*>): List<String>? = this?.map { it.trim() }?.takeIf { it.size == names.size }

    private fun List<String>?.single(): String? = this?.map { it.trim() }?.filter { it.isNotEmpty() }?.singleOrNull()
}
