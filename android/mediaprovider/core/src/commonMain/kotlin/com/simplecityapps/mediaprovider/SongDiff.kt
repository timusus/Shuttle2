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
     * Whether [updated] differs from [oldData] in a field the update writes. What it doesn't write (play stats, exclusion,
     * stream properties, the album identity) is taken from [oldData] before comparing; a remote song's favourite is
     * written from its server, so it counts there.
     */
    override fun isChanged(
        oldData: Song,
        updated: Song
    ): Boolean {
        // The database stores epoch milliseconds, but Jellyfin and Emby parse ISO strings with up to seven fractional
        // digits, so an instant only counts as changed at the stored precision
        fun Instant?.atStoredPrecision(): Instant? = this?.toEpochMilliseconds()?.let(Instant::fromEpochMilliseconds)

        val old = oldData.copy(
            lastModified = oldData.lastModified.atStoredPrecision(),
            dateAdded = oldData.dateAdded.atStoredPrecision(),
            favouritedAt = oldData.favouritedAt.atStoredPrecision()
        )

        return updated.keepingStoredBitDepth(old).copy(
            lastPlayed = old.lastPlayed,
            lastCompleted = old.lastCompleted,
            playCount = old.playCount,
            playbackPosition = old.playbackPosition,
            blacklisted = old.blacklisted,
            mediaProvider = old.mediaProvider,
            bitRate = old.bitRate,
            sampleRate = old.sampleRate,
            channelCount = old.channelCount,
            audioCodec = old.audioCodec,
            albumIdentity = old.albumIdentity,
            // Stored as a year only
            date = if (updated.date?.year == old.date?.year) old.date else updated.date,
            // A server stamps all of its favourites with the sync time, and the merge keeps the stored time anyway,
            // so only a flip in favourited-ness counts as a change
            favouritedAt = if (old.mediaProvider.remote && (updated.favouritedAt != null) != (old.favouritedAt != null)) {
                updated.favouritedAt.atStoredPrecision()
            } else {
                old.favouritedAt
            },
            lastModified = updated.lastModified.atStoredPrecision(),
            dateAdded = updated.dateAdded.atStoredPrecision()
        ) != old
    }

    override fun update(
        oldData: Song,
        newData: Song
    ): Song = newData.keepingStoredBitDepth(oldData).copy(
        id = oldData.id,
        // A provider with no date for the song keeps the one from its first import, rather than looking newly added
        lastModified = newData.lastModified ?: oldData.lastModified,
        // The server's date for a remote song, which replaces an older import stamp; otherwise (local songs) the one
        // stamped when the song first reached the library, which a tag edit or rescan doesn't move
        dateAdded = newData.dateAdded ?: oldData.dateAdded
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
}
