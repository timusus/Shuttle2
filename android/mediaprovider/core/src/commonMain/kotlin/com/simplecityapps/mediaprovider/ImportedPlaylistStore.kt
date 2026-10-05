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
     * Makes the playlists stored from [type]'s server match its [listing], as one transaction: stores each playlist it read
     * as [storePlaylist] does, then deletes each stored playlist that holds no songs after that, and, if the listing is
     * [listingComplete], each one from a source it no longer lists. One from an [unread][MediaImporter.PlaylistListing.unread]
     * source is never deleted, and the playlists made in S2 are left as they are.
     */
    suspend fun reconcilePlaylists(
        type: MediaProviderType,
        listing: MediaImporter.PlaylistListing,
        listingComplete: Boolean
    )
}
