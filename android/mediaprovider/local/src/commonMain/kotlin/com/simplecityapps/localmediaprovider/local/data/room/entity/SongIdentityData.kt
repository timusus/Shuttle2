package com.simplecityapps.localmediaprovider.local.data.room.entity

import com.simplecityapps.shuttle.model.AlbumIdentity
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.AlbumIdentityTags
import com.simplecityapps.shuttle.model.AlbumKeyRekey
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The columns of a song that [AlbumIdentityRule] reads: what a library's album identities are resolved from, without
 * reading every song whole. [SONG_IDENTITY_QUERY] selects them.
 */
data class SongIdentityData(
    val id: Long,
    val album: String?,
    val albumArtist: String?,
    val albumArtists: List<String>?,
    val artists: List<String>,
    val compilation: Boolean?,
    val mbAlbumId: String?,
    val serverAlbumId: String?,
    val mediaProvider: MediaProviderType,
    val path: String
) {
    fun toTags(): AlbumIdentityTags = AlbumIdentityTags(id, album, albumArtist, albumArtists, artists, compilation, mbAlbumId, serverAlbumId, mediaProvider, path)
}

/** Every song's [SongIdentityData], in id order. */
const val SONG_IDENTITY_QUERY = "SELECT id, album, albumArtist, albumArtists, artists, compilation, mbAlbumId, serverAlbumId, mediaProvider, path FROM songs ORDER BY id"

/**
 * The library's album identities, by song id, from all its rows. Reading them again while the library hasn't changed
 * (Home's sections, a playlist, a restored queue, one after another) reuses the last result rather than resolving again.
 */
@OptIn(ExperimentalAtomicApi::class)
fun List<SongIdentityData>.albumIdentities(): Map<Long, AlbumIdentity> {
    lastResolved.load()?.let { (rows, identities) -> if (rows == this) return identities }
    return AlbumIdentityRule.resolve(map { it.toTags() }).also { identities -> lastResolved.store(this to identities) }
}

@OptIn(ExperimentalAtomicApi::class)
private val lastResolved = AtomicReference<Pair<List<SongIdentityData>, Map<Long, AlbumIdentity>>?>(null)

/** The [AlbumKeyRekey] over all these rows (the library's), reused while the library hasn't changed, as [albumIdentities] is. */
@OptIn(ExperimentalAtomicApi::class)
fun List<SongIdentityData>.albumKeyRekey(): AlbumKeyRekey {
    lastRekey.load()?.let { (rows, rekey) -> if (rows == this) return rekey }
    val identities = albumIdentities()
    return AlbumKeyRekey(mapNotNull { row -> identities[row.id]?.let { row.toTags() to it } }).also { rekey -> lastRekey.store(this to rekey) }
}

@OptIn(ExperimentalAtomicApi::class)
private val lastRekey = AtomicReference<Pair<List<SongIdentityData>, AlbumKeyRekey>?>(null)
