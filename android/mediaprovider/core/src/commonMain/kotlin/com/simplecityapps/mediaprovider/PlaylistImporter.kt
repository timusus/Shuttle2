package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.MediaImporter.PlaylistImportResult
import com.simplecityapps.mediaprovider.MediaImporter.PlaylistListing
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** The playlist half of one provider's import: fetches its playlists and stores them. */
internal class PlaylistImporter(
    private val songRepository: SongRepository,
    private val playlistStore: ImportedPlaylistStore,
    private val preferenceManager: GeneralPreferenceManager,
    /** Sends the edits made in S2 to a server's playlists before that server's playlists are read again (#916). */
    private val playlistSync: ServerPlaylistSync?
) {
    private val logger = Logger.tagged("MediaImporter")

    /**
     * Fetches and stores [mediaProvider]'s playlists. A remote server's are reconciled with its listing, deleting the stale
     * ones, only when this pass stored its songs ([songsStored]) and the library holds some: against a song import that
     * failed, or a library that holds none of its songs yet, every playlist would match nothing and be deleted. Otherwise
     * the playlists that hold songs are stored and nothing is deleted.
     *
     * With [reuseVersions], a reconcile passes the provider the version each stored playlist had when it was last read, so it
     * reads only the playlists changed on the server since (#843). The versions are recorded once a reconcile stores them.
     */
    fun import(
        mediaProvider: MediaProvider,
        timings: ImportTimings,
        songsStored: Boolean,
        reuseVersions: Boolean
    ): Flow<FlowEvent<PlaylistImportResult, MessageProgress>> = flow {
        // Straight from the database: the songs this pass just stored (or the last pass did) may not be in the shared list yet
        val existingSongs = songRepository.loadProviderSongs(mediaProvider.type)
        val reconcile = mediaProvider.type.remote && songsStored && existingSongs.isNotEmpty()
        if (mediaProvider.type.remote && !reconcile) {
            logger.info { "${mediaProvider.type} playlists stored without reconciling: songs stored $songsStored, library songs ${existingSongs.size}" }
        }

        val source = mediaProvider.type.name
        val knownVersions =
            if (reconcile && reuseVersions) {
                // Only a playlist still stored, holding songs, can be left as it is
                val stored = playlistStore.storedPlaylistIds(mediaProvider.type)
                preferenceManager.playlistVersions(source).filterKeys { externalId -> externalId in stored }
            } else {
                emptyMap()
            }

        val findPlaylistsMark = TimeSource.Monotonic.markNow()
        mediaProvider.findPlaylists(existingSongs, knownVersions).collect { event ->
            when (event) {
                is FlowEvent.Progress -> {
                    emit(FlowEvent.Progress<PlaylistImportResult, MessageProgress>(event.data))
                }

                is FlowEvent.Success -> {
                    // The edits are read as the playlists are written, with the queue held, so none lands in between
                    val write: suspend (Set<String>) -> Unit = { held -> writePlaylists(mediaProvider.type, event, held, reconcile, existingSongs) }
                    playlistSync?.withPendingPlaylistIds(mediaProvider.type, write) ?: write(emptySet())
                }

                is FlowEvent.Failure -> {
                    emit(event)
                }
            }
        }
        timings.findPlaylists = findPlaylistsMark.elapsedNow()
        emit(FlowEvent.Success(PlaylistImportResult(mediaProvider.type)))
    }

    /** Stores the playlists [event] found, leaving those in [held] (edited in S2, not yet on the server) as they are. */
    private suspend fun writePlaylists(
        type: MediaProviderType,
        event: FlowEvent.Success<PlaylistListing>,
        held: Set<String>,
        reconcile: Boolean,
        existingSongs: List<Song>
    ) {
        val source = type.name
        if (held.isNotEmpty()) {
            logger.info { "$type: ${held.size} playlists left as they are until their edits reach the server" }
        }
        if (reconcile) {
            val listing = event.result.holdingBack(held)
            val lastServerSongs = preferenceManager.playlistServerSongs(source)
            val songIds by lazy { existingSongs.associate { song -> song.path to song.id } }
            playlistStore.reconcilePlaylists(
                type,
                listing,
                listingComplete = event.complete,
                lastServerSongs = lastServerSongs.mapValues { (_, paths) -> paths.mapNotNullTo(HashSet()) { path -> songIds[path] } }
            )
            preferenceManager.setPlaylistVersions(source, listing.versions)
            preferenceManager.setPlaylistServerSongs(source, serverSongsAfter(listing, lastServerSongs))
        } else {
            event.result.playlists.forEach { playlistUpdateData ->
                if (playlistUpdateData.songs.isNotEmpty() && playlistUpdateData.externalId !in held) {
                    playlistStore.storePlaylist(playlistUpdateData)
                }
            }
        }
    }

    /**
     * This listing with the playlists in [held] left unchanged: those S2 has edits to that the server hasn't been sent yet. Their
     * versions are dropped, so the next sync reads them again once the server has the edits.
     */
    private fun PlaylistListing.holdingBack(held: Set<String>): PlaylistListing = if (held.isEmpty()) {
        this
    } else {
        val listed = playlists.map { playlist -> playlist.externalId } + unread + unchanged
        PlaylistListing(
            playlists = playlists.filterNot { playlist -> playlist.externalId in held },
            unread = unread - held,
            unchanged = unchanged + held.filter { externalId -> externalId in listed },
            versions = versions - held
        )
    }

    /**
     * The songs, by path, each playlist holds on the server after [listing], from those it held at the last read
     * ([lastServerSongs]): a playlist read in full holds what was read; one read in part, or left unchanged, holds what it
     * did, and anything the part read found. A playlist the listing doesn't name is gone.
     */
    private fun serverSongsAfter(
        listing: PlaylistListing,
        lastServerSongs: Map<String, List<String>>
    ): Map<String, List<String>> {
        val read = listing.playlists.associate { playlist -> playlist.externalId to playlist.songs.map { song -> song.path } }
        val readInFull = read.filterKeys { externalId -> externalId !in listing.unread }
        val kept = (listing.unread + listing.unchanged).associateWith { externalId ->
            (lastServerSongs[externalId].orEmpty() + read[externalId].orEmpty()).distinct()
        }
        return (kept + readInFull).filterValues { paths -> paths.isNotEmpty() }
    }
}
