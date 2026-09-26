package com.simplecityapps.shuttle.entitlement

import com.simplecityapps.mediaprovider.ServerStreamPolicy
import com.simplecityapps.shuttle.downloads.SongDownload
import com.simplecityapps.shuttle.downloads.SongDownloadRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.trial.ServerAccessGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Server streaming needs Pro or the trial, except for a song that's already downloaded, which always plays.
 * A single instance ([Singleton]), so [gatedSongs] sees every denial regardless of which injection site
 * triggers it.
 */
@Singleton
class EntitledServerStreamPolicy
@Inject
constructor(
    private val songDownloadRepository: SongDownloadRepository,
    private val serverAccessGate: ServerAccessGate
) : ServerStreamPolicy {
    private val _gatedSongs = MutableSharedFlow<Song>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A song already in the queue, each time it's skipped because streaming it needs S2 Pro. */
    val gatedSongs: SharedFlow<Song> = _gatedSongs.asSharedFlow()

    override suspend fun allows(song: Song): Boolean {
        if (songDownloadRepository.getDownload(song.path)?.state == SongDownload.State.Completed) return true
        val allowed = serverAccessGate.tryStreamFromServer()
        if (!allowed) _gatedSongs.tryEmit(song)
        return allowed
    }
}
