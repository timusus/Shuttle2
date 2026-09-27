package com.simplecityapps.mediaprovider

/** Where [MediaImporter] stores the playlists a [MediaProvider] finds. */
interface ImportedPlaylistStore {
    /**
     * Stores [playlist] as one transaction, reading what's stored from the database rather than a shared flow that can lag a
     * write: creates it the first time its provider finds its source ([MediaImporter.PlaylistUpdateData.externalId]), and
     * afterwards updates the playlist imported from that same source, never one that merely shares its name.
     */
    suspend fun storePlaylist(playlist: MediaImporter.PlaylistUpdateData)
}
