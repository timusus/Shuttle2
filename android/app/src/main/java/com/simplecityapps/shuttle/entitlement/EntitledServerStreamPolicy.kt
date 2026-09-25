package com.simplecityapps.shuttle.entitlement

import com.simplecityapps.mediaprovider.ServerStreamPolicy
import com.simplecityapps.shuttle.downloads.SongDownload
import com.simplecityapps.shuttle.downloads.SongDownloadRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.trial.ServerAccessGate
import javax.inject.Inject

/** Server streaming needs Pro or the trial, except for a song that's already downloaded, which always plays. */
class EntitledServerStreamPolicy
@Inject
constructor(
    private val songDownloadRepository: SongDownloadRepository,
    private val serverAccessGate: ServerAccessGate
) : ServerStreamPolicy {
    override suspend fun allows(song: Song): Boolean = songDownloadRepository.getDownload(song.path)?.state == SongDownload.State.Completed ||
        serverAccessGate.tryStreamFromServer()
}
