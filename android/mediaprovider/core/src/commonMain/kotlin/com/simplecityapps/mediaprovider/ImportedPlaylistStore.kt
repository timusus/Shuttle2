package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType

/** Where [MediaImporter] stores the playlists a [MediaProvider] finds. */
interface ImportedPlaylistStore {
    /**
     * Stores [playlist] as one transaction, reading what's stored from the database rather than a shared flow that can lag a
     * write: creates it the first time its provider finds its source ([MediaImporter.PlaylistUpdateData.externalId]), and
     * afterwards updates the playlist imported from that same source, never one that merely shares its name.
     */
    suspend fun storePlaylist(playlist: MediaImporter.PlaylistUpdateData)

    /**
     * Makes the playlists stored from [type]'s server match its [listing], as one transaction: gives each playlist it read
     * in full the server's name and songs, in the server's order, dropping the songs [lastServerSongs] (by external id: the
     * ids of the songs it held on the server when last read) shows the server has removed since, and keeping, after them,
     * those added to it in S2, which the server never heard of. One it read in part is stored as [storePlaylist] does. Then
     * it deletes each stored playlist that holds no songs after that, and, if the listing is [listingComplete], each one from
     * a source it no longer lists. One from an [unread][MediaImporter.PlaylistListing.unread] or
     * [unchanged][MediaImporter.PlaylistListing.unchanged] source is never deleted, and the playlists made in S2 are left
     * as they are.
     */
    suspend fun reconcilePlaylists(
        type: MediaProviderType,
        listing: MediaImporter.PlaylistListing,
        listingComplete: Boolean,
        lastServerSongs: Map<String, Set<Long>>
    )

    /** The [externalId][MediaImporter.PlaylistUpdateData.externalId]s of the playlists stored from [type] that hold songs. */
    suspend fun storedPlaylistIds(type: MediaProviderType): Set<String>
}
