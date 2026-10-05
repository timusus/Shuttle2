package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant

class SongDiff(
    existingData: List<Song>,
    newData: List<Song>,
    deleteMissing: Boolean = true
) : Diff<Song>(existingData, newData, deleteMissing) {
    override fun key(item: Song): Any = item.path

    /**
     * Whether [updated] differs from [oldData] in a field the update writes. What it doesn't write (exclusion, stream
     * properties, the album identity, a local song's play stats) is taken from [oldData] before comparing; a remote song's
     * favourite and play stats are merged in from its server (see [mergedPlayStats]), so they count there.
     */
    override fun isChanged(
        oldData: Song,
        updated: Song
    ): Boolean {
        // Each provider maps its times at the stored precision, whole milliseconds, so the stored and the read compare exactly
        val old = oldData

        return updated.keepingStoredBitDepth(old).keepingStoredStreamProperties(old).copy(
            lastPlayed = if (old.mediaProvider.remote) mergedPlayStats(old, updated).lastPlayed else old.lastPlayed,
            lastCompleted = old.lastCompleted,
            playCount = if (old.mediaProvider.remote) mergedPlayStats(old, updated).playCount else old.playCount,
            playbackPosition = old.playbackPosition,
            blacklisted = old.blacklisted,
            mediaProvider = old.mediaProvider,
            albumIdentity = old.albumIdentity,
            // The stored songs are read without their lyrics (#873), so there are none to compare: a changed tag moves
            // the file's modified time, and the update writes the new lyrics
            lyrics = old.lyrics,
            // Stored as a year only
            date = if (updated.date?.year == old.date?.year) old.date else updated.date,
            // A server stamps all of its favourites with the sync time, and the merge keeps the stored time anyway,
            // so only a flip in favourited-ness counts as a change
            favouritedAt = if (old.mediaProvider.remote && (updated.favouritedAt != null) != (old.favouritedAt != null)) {
                updated.favouritedAt
            } else {
                old.favouritedAt
            },
            lastModified = updated.lastModified,
            dateAdded = updated.dateAdded
        ) != old
    }

    override fun update(
        oldData: Song,
        newData: Song
    ): Song = newData.keepingStoredBitDepth(oldData).keepingStoredStreamProperties(oldData).copy(
        id = oldData.id,
        playCount = mergedPlayStats(oldData, newData).playCount,
        lastPlayed = mergedPlayStats(oldData, newData).lastPlayed,
        // A provider with no date for the song keeps the one from its first import, rather than looking newly added
        lastModified = newData.lastModified ?: oldData.lastModified,
        // The server's date for a remote song, which replaces an older import stamp; otherwise (local songs) the one
        // stamped when the song first reached the library, which a tag edit or rescan doesn't move
        dateAdded = newData.dateAdded ?: oldData.dateAdded
    )

    /**
     * The stream properties a provider reports, else the stored ones: the MediaStore scan reports none, and a TagLib
     * rescan's would otherwise be wiped by it.
     */
    private fun Song.keepingStoredStreamProperties(old: Song): Song = copy(
        bitRate = bitRate ?: old.bitRate,
        sampleRate = sampleRate ?: old.sampleRate,
        channelCount = channelCount ?: old.channelCount,
        audioCodec = audioCodec ?: old.audioCodec
    )

    /**
     * A remote lossless song that arrives without a bit depth keeps the stored one: its server only reports the depth
     * through a request that can fail (Plex), and a song whose codec turned lossy still clears it.
     */
    private fun Song.keepingStoredBitDepth(old: Song): Song = if (bitDepth == null && old.mediaProvider.remote && isLosslessCodec(audioCodec)) {
        copy(bitDepth = old.bitDepth)
    } else {
        this
    }

    private class PlayStats(
        val playCount: Int,
        val lastPlayed: Instant?
    )

    /**
     * A remote song's play stats once its server's are merged with the stored (local) ones: the larger play count and the
     * later last-played time. The app reports its own plays to the server, so the server's count already includes them
     * and adding the two would count every play twice; taking the maximum never loses a play made on either side. A
     * local song's stats come from this device only, so [oldData]'s are kept. The database applies the same rule in SQL
     * (`SongDataDao.applyServerPlayStats`), so a play finishing during the sync isn't overwritten.
     */
    private fun mergedPlayStats(
        oldData: Song,
        newData: Song
    ): PlayStats = if (oldData.mediaProvider.remote) {
        PlayStats(
            playCount = maxOf(oldData.playCount, newData.playCount),
            lastPlayed = listOfNotNull(oldData.lastPlayed, newData.lastPlayed).maxOrNull()
        )
    } else {
        PlayStats(oldData.playCount, oldData.lastPlayed)
    }
}
