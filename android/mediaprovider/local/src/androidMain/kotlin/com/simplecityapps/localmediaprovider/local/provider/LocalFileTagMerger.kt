package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.shuttle.model.Song

/**
 * The rule both local providers share for skipping a file read: opening a file and parsing its tags costs far more than
 * listing it, so a file whose song was imported before, and whose path, size and modified date (epoch milliseconds, which
 * is what [Song.lastModified] stores, whichever source it came from) are all unchanged, keeps that song's stored tags.
 *
 * [readUnchanged] turns reuse off, for the one-off backfill of songs imported before their tags were read from the file.
 */
internal class LocalFileTagMerger(
    existingSongs: List<Song>,
    private val readUnchanged: Boolean = false
) {
    private val existingSongsByPath = existingSongs.associateBy { it.path }

    /** The stored song for the file at [path] if it hasn't changed since the last import, else null: read it. */
    fun unchangedSong(
        path: String,
        size: Long,
        lastModified: Long
    ): Song? {
        if (readUnchanged) return null
        val existing = existingSongsByPath[path] ?: return null
        if (existing.lastModified?.toEpochMilliseconds() != lastModified || existing.size != size) return null
        return existing
    }

    /** [this], a song built from the file listing, with the tags [unchangedSong] returned in place of the ones read from the file. */
    fun Song.withStoredTags(existing: Song): Song = copy(
        name = existing.name,
        artists = existing.artists,
        albumArtist = existing.albumArtist,
        album = existing.album,
        track = existing.track,
        disc = existing.disc,
        date = existing.date,
        replayGainTrack = existing.replayGainTrack,
        replayGainAlbum = existing.replayGainAlbum,
        albumArtists = existing.albumArtists,
        artistsTag = existing.artistsTag,
        artistDisplay = existing.artistDisplay,
        compilation = existing.compilation,
        mbTrackId = existing.mbTrackId,
        mbAlbumId = existing.mbAlbumId,
        mbReleaseGroupId = existing.mbReleaseGroupId,
        mbArtistIds = existing.mbArtistIds,
        mbAlbumArtistIds = existing.mbAlbumArtistIds
    )
}
