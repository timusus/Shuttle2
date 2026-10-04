package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song

class SongDiff(
    existingData: List<Song>,
    newData: List<Song>,
    deleteMissing: Boolean = true
) : Diff<Song>(existingData, newData, deleteMissing) {
    override fun key(item: Song): Any = item.path

    /**
     * Whether [updated] differs from [oldData] in a field the update writes. What it doesn't write (play stats, exclusion,
     * stream properties, the album identity) is taken from [oldData] before comparing; a remote song's favourite is
     * written from its server, so it counts there.
     */
    override fun isChanged(
        oldData: Song,
        updated: Song
    ): Boolean = updated.copy(
        lastPlayed = oldData.lastPlayed,
        lastCompleted = oldData.lastCompleted,
        playCount = oldData.playCount,
        playbackPosition = oldData.playbackPosition,
        blacklisted = oldData.blacklisted,
        mediaProvider = oldData.mediaProvider,
        bitRate = oldData.bitRate,
        sampleRate = oldData.sampleRate,
        channelCount = oldData.channelCount,
        audioCodec = oldData.audioCodec,
        albumIdentity = oldData.albumIdentity,
        // Stored as a year only
        date = if (updated.date?.year == oldData.date?.year) oldData.date else updated.date,
        favouritedAt = if (oldData.mediaProvider.remote) updated.favouritedAt else oldData.favouritedAt
    ) != oldData

    override fun update(
        oldData: Song,
        newData: Song
    ): Song = newData.copy(
        id = oldData.id,
        // A provider with no date for the song keeps the one from its first import, rather than looking newly added
        lastModified = newData.lastModified ?: oldData.lastModified,
        // The server's date for a remote song, which replaces an older import stamp; otherwise (local songs) the one
        // stamped when the song first reached the library, which a tag edit or rescan doesn't move
        dateAdded = newData.dateAdded ?: oldData.dateAdded
    )
}
