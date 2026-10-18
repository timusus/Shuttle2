package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song

/**
 * The playlists a provider found: [playlists], each with the songs of its source, and [unread], the [PlaylistUpdateData.externalId]s
 * of those it listed but couldn't read all the songs of (one it read in part is in [playlists] too, so what it read is
 * added). A playlist stored from an [unread] source is never deleted, never taken as gone from it. [unchanged] are those
 * whose songs it didn't read, as the server reports the version they were last read at; they stay as they are.
 * [versions] holds the server's version of each playlist it read in full or left [unchanged], by
 * [PlaylistUpdateData.externalId], for the next sync to pass back.
 */
data class PlaylistListing(
    val playlists: List<PlaylistUpdateData>,
    val unread: Set<String> = emptySet(),
    val unchanged: Set<String> = emptySet(),
    val versions: Map<String, String> = emptyMap()
)

/** A playlist [mediaProviderType] found, holding the [songs] of its source, which [externalId] identifies within that provider. */
data class PlaylistUpdateData(
    val mediaProviderType: MediaProviderType,
    val name: String,
    val songs: List<Song>,
    val externalId: String
)
