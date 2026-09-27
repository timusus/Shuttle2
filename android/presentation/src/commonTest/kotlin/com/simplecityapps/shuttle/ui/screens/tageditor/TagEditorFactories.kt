package com.simplecityapps.shuttle.ui.screens.tageditor

import com.simplecityapps.createAudioFile
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.model.Song

/** A [WriteConsent] test double: no platform request to launch, just a marker the fake hands back. */
object FakeWriteConsent : WriteConsent

/** Tag access over an in-memory map of song id to file tags; a song without an entry can't be read. */
class FakeTagFileAccess(
    var files: Map<Long, AudioFile> = emptyMap(),
) : TagFileAccess {
    /** Songs whose writes fail. */
    var failingSongIds: Set<Long> = emptySet()

    /** Every write as (song id, metadata), in order. */
    val writes = mutableListOf<Pair<Long, Map<String, List<String>>>>()

    /** The consent request a save must launch first, if any. */
    var consent: WriteConsent? = null

    override suspend fun read(song: Song): AudioFile? = files[song.id]

    override suspend fun writeConsent(songs: List<Song>): WriteConsent? = consent

    override suspend fun write(
        song: Song,
        metadata: Map<String, List<String>>,
    ): Boolean {
        writes += song.id to metadata
        return song.id !in failingSongIds
    }
}
